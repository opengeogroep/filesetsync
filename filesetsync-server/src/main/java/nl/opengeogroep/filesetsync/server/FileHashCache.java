package nl.opengeogroep.filesetsync.server;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
import org.apache.commons.lang3.mutable.MutableLong;
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

    @Override
    public void contextInitialized(ServletContextEvent sce) {

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
            File cacheDir = new File(fs.getPath() + File.separator + CACHE_DIR);
            log.info("Creating Ehcache for fileset " + fs.getName() + " in directory " + cacheDir);
            Configuration cacheManagerConfig = new Configuration()
                    .name(name)
                    .diskStore(new DiskStoreConfiguration()
                            .path(cacheDir.getAbsolutePath()));
            CacheConfiguration cacheConfig = new CacheConfiguration(name, maxElementsInMemory)
                    .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LFU)
                    .eternal(false)
                    .timeToLiveSeconds(60 * 60 * 24)
                    .persistence(new PersistenceConfiguration().strategy(Strategy.LOCALTEMPSWAP));
            cacheManagerConfig.addCache(cacheConfig);

            CacheManager cacheManager = new CacheManager(cacheManagerConfig);
            cacheManagers.put(name, cacheManager);
            caches.put(name, cacheManager.getCache(name));
            // TODO read persisted cache
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        for(CacheManager cm: cacheManagers.values()) {
            // TODO: persist cache
            cm.shutdown();
        }
    }

    public static String getCachedFileHash(ServerFileset fileset, File f, long fileLastModified, MutableLong hashBytesAccumulator, MutableLong hashTimeMillisAccumulator) throws IOException {
        Cache cache = caches.get(fileset.getName());
        Element e  = cache.get(f.getCanonicalPath());
        String hash = null;
        if(e != null) {
            String[] parts = ((String)e.getObjectValue()).split(",");
            long lastModified = Long.parseLong(parts[0]);
            if(lastModified == fileLastModified) {
                hash = (String)e.getObjectValue();
            }
        }
        if(hash == null) {
            hash = FileRecord.calculateHash(f, hashTimeMillisAccumulator);
            hashBytesAccumulator.add(f.length());
            cache.put(new Element(f.getCanonicalPath(), f.lastModified() + "," + hash));
        }
        return hash;
    }
}