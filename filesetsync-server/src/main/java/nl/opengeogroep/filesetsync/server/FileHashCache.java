package nl.opengeogroep.filesetsync.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration.Strategy;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import nl.opengeogroep.filesetsync.FileRecord;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/*
 * Copyright (C) 2014 Expression organization is undefined on line 4, column 61 in file:///home/matthijsln/filesetsync/filesetsync-server/licenseheader.txt.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 *
 * @author matthijsln
 */
public class FileHashCache implements ServletContextListener {
    private static final Log log = LogFactory.getLog(FileHashCache.class);

    private static final String CACHE_DIR = "filesetsync-hashcache";

    public static Map<String,CacheManager> cacheManagers = new HashMap();
    public static Map<String,Cache> caches = new HashMap();

    private static String getCacheDir(String name) {
        return ServerSyncConfig.getInstance().getFileset(name).getPath() + File.separator + CACHE_DIR;
    }

    private static String getPersistedCacheFile(String name) {
        return getCacheDir(name) + File.separator + name + "-hashes.txt.gz";
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("*** initializing ***");

        int maxElementsInMemory = 10000;
        try {
            String s = sce.getServletContext().getInitParameter("hashcache.maxElementsInMemory");
            if(s != null) {
                maxElementsInMemory = Integer.parseInt(s);
            }
        } catch(Exception e) {
        }

        // Create caches for all filesets
        for(String name: ServerSyncConfig.getInstance().getFilesetNames()) {
            ServerFileset fs = ServerSyncConfig.getInstance().getFileset(name);
            File f = new File(fs.getPath());

            if(f.isFile()) {
                continue;
            }
            File cacheDir = new File(getCacheDir(name));
            log.info("Creating Ehcache for fileset " + fs.getName() + " in directory " + cacheDir);
            Configuration cacheManagerConfig = new Configuration()
                    .name(name)
                    .diskStore(new DiskStoreConfiguration()
                            .path(cacheDir.getAbsolutePath()));
            CacheConfiguration cacheConfig = new CacheConfiguration(name, maxElementsInMemory)
                    .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LFU)
                    .eternal(false)
                    .timeToIdleSeconds(60 * 60 * 24)
                    .persistence(new PersistenceConfiguration().strategy(Strategy.LOCALTEMPSWAP));
            cacheManagerConfig.addCache(cacheConfig);

            CacheManager cacheManager = new CacheManager(cacheManagerConfig);
            cacheManagers.put(name, cacheManager);
            Cache cache = cacheManager.getCache(name);
            caches.put(name, cache);

            File persistedCache = new File(getPersistedCacheFile(name));
            if(persistedCache.exists() && persistedCache.canRead()) {
                log.info(String.format("Reading persisted cache for \"%s\" from \"%s\"...", name, persistedCache));
                try (
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                                    new GZIPInputStream(
                                        new FileInputStream(persistedCache))))) {

                    long startTime = System.currentTimeMillis();

                    String line;
                    while((line = br.readLine()) != null) {
                        String[] s = line.split(":", 2);
                        cache.put(new Element(s[0], s[1]));
                    }
                    log.info(String.format("Cache size is %d, read in %s", cache.getSize(), DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, false)));
                } catch(IOException e) {
                    log.error("Exception reading persisted cache", e);
                }
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("*** destroying ***");

        for(Map.Entry<String,CacheManager> entry: cacheManagers.entrySet()) {
            String name = entry.getKey();
            CacheManager manager = entry.getValue();
            Cache cache = manager.getCache(name);
            log.info(String.format("Evicting expired elements from cache \"%s\"", name));
            cache.evictExpiredElements();
            log.info(String.format("Persisting cache \"%s\" (size %d)...", name, cache.getSize()));
            long startTime = System.currentTimeMillis();
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                            new GZIPOutputStream(
                                    new FileOutputStream(getPersistedCacheFile(name)))))) {
                for(Object key: cache.getKeys()) {
                    Element e = cache.get(key);
                    if(e != null) {
                        writer.write(key + ":" + e.getObjectValue());
                        writer.newLine();
                    }
                }
                log.info("Done persisting cache in " + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, false));
            } catch(IOException e) {
                log.error(String.format("Error persisting cache \"%s\": %s", name, ExceptionUtils.getMessage(e)), e);
            }
            manager.shutdown();
        }
    }

    public static String getCachedFileHash(ServerFileset fileset, File f, long fileLastModified, MutableLong hashBytesAccumulator, MutableLong hashTimeMillisAccumulator) throws IOException {
        Cache cache = caches.get(fileset.getName());
        String canonicalPath = f.getCanonicalPath();
        Element e  = cache.get(canonicalPath);
        String hash = null;
        if(e != null) {
            String[] parts = ((String)e.getObjectValue()).split(",", 2);
            long lastModified = Long.parseLong(parts[0]);
            if(lastModified == fileLastModified) {
                hash = parts[1];
            }
        }
        if(hash == null) {
            hash = FileRecord.calculateHash(f, hashTimeMillisAccumulator);
            hashBytesAccumulator.add(f.length());
            cache.put(new Element(canonicalPath, fileLastModified + "," + hash));
        }
        return hash;
    }
}