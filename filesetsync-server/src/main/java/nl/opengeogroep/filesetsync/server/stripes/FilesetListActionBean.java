/*
 * Copyright (C) 2014 B3Partners B.V.
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

package nl.opengeogroep.filesetsync.server.stripes;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ErrorResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.filesetsync.FileRecord;
import static nl.opengeogroep.filesetsync.FileRecord.TYPE_DIRECTORY;
import static nl.opengeogroep.filesetsync.FileRecord.TYPE_FILE;
import nl.opengeogroep.filesetsync.protocol.BufferedFileListEncoder;
import static nl.opengeogroep.filesetsync.protocol.Protocol.FILELIST_ENCODING;
import nl.opengeogroep.filesetsync.server.FileHashCache;
import nl.opengeogroep.filesetsync.server.ServerFileset;
import static nl.opengeogroep.filesetsync.util.FormatUtil.dateToString;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/fileset/list/{filesetPath}")
public class FilesetListActionBean extends FilesetBaseActionBean {

    private static final Log log = LogFactory.getLog("api.list");

    @Validate
    private boolean hash = false;

    @Validate
    private String regexp;

    @Override
    protected final String getLogName() {
        return "api.list";
    }

    public boolean isHash() {
        return hash;
    }

    public void setHash(boolean hash) {
        this.hash = hash;
    }

    public String getRegexp() {
        return regexp;
    }

    public void setRegexp(String regexp) {
        this.regexp = regexp;
    }

    private class FilesetListingResolution extends StreamingResolution {

        private final ServerFileset fileset;
        private final Iterable<FileRecord> iterable;

        private final long startTime;
        private long totalBytes;
        private int dirs, files;

        public FilesetListingResolution(ServerFileset fileset, Iterable<FileRecord> iterable) {
            super("text/plain");
            this.fileset = fileset;
            this.iterable = iterable;

            startTime = System.currentTimeMillis();
        }

        @Override
        public void stream(HttpServletResponse response) throws IOException  {
            response.setCharacterEncoding(FILELIST_ENCODING);

            String acceptEncoding = getContext().getRequest().getHeader("Accept-Encoding");

            OutputStream out;
            if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                response.setHeader("Content-Encoding", "gzip");
                out = new GZIPOutputStream(response.getOutputStream());
            } else {
                out = response.getOutputStream();
            }

            final BufferedFileListEncoder encoder = new BufferedFileListEncoder(out);

            String cacheDir = new File(FileHashCache.getCacheDir(fileset.getName())).getCanonicalPath();
            MutableLong hashBytes = new MutableLong();
            MutableLong hashTime = new MutableLong();
            int hashErrors = 0;
            try {
                boolean hashLogMsg = false;
                for(FileRecord fr: iterable) {
                    String canonicalPath = fr.getFile().getCanonicalPath();
                    if(canonicalPath.startsWith(cacheDir)) {
                        continue;
                    }
                    if(TYPE_FILE == fr.getType()) {
                        try {
                            if(hash) {
                                if(!hashLogMsg) {
                                    log.debug("start hashing");
                                    hashLogMsg = true;
                                }
                                fr.setHash(FileHashCache.getCachedFileHash(fileset, fr.getFile(), fr.getLastModified(), hashBytes, hashTime));
                            }
                            files++;
                            totalBytes += fr.getSize();
                            encoder.write(fr);
                        } catch(IOException e) {
                            if(e.getClass().getName().endsWith("ClientAbortException")) {
                                log.warn("received client abort calculating hash of " + canonicalPath);
                                return;
                            } else {
                                log.error("error calculating hash of " + canonicalPath + ": " + ExceptionUtils.getMessage(e));
                                if(++hashErrors > 10) {
                                    // TODO all errors may be from a single unaccessible directory,
                                    // maybe skip all paths starting with dir part of canonicalPath...
                                    log.error("too many hash errors, aborting");
                                    return;
                                }
                            }
                        }
                    } else if(TYPE_DIRECTORY == fr.getType()) {
                        dirs++;
                        encoder.write(fr);
                    } else {
                        log.error("not a file or directory (special or deleted file), ignoring " + fr.getFile());
                    }
                }
            } finally {
                IOUtils.closeQuietly(encoder);
                IOUtils.closeQuietly(out);

                String hashInfo;
                if(hash) {
                    hashInfo = String.format(", hashed %d KB (cache hit rate %.1f%%), hash time %s, hash speed %s",
                            hashBytes.getValue() / 1024,
                            Math.abs(100-(100.0/totalBytes*hashBytes.getValue())),
                            DurationFormatUtils.formatDurationWords(hashTime.getValue(), true, false),
                            (hashTime.getValue() < 100 ? "n/a" : Math.round(hashBytes.getValue() / 1024.0 / (hashTime.getValue() / 1000.0)) + " KB/s")
                    );
                } else {
                    hashInfo = " (no hashing)";
                }

                log.info(String.format("listed %d directories and %d files, total size %d KB, time %s%s",
                        dirs,
                        files,
                        totalBytes / 1024,
                        DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, false),
                        hashInfo
                ));
            }
        }
    }

    public Resolution list() throws Exception {

        final ServerFileset theFileset = getFileset();

        MutableInt noRegexpMatches = new MutableInt();

        long ifModifiedSince = getContext().getRequest().getDateHeader("If-Modified-Since");

        if(ifModifiedSince == -1) {
            log.info("listing " + getLocalSubPath());
            // Stream file records directly to output without buffering all
            // records in memory
            // return new FilesetListingResolution(ServerFileset.getFileRecordsInDir(getLocalSubPath());
            return new FilesetListingResolution(theFileset, FileRecord.getFileRecordsInDir(getLocalSubPath(), regexp, noRegexpMatches));
        } else {
            // A conditional HTTP request with If-Modified-Since is checked
            // against the latest last modified date of all the files and
            // directories in the fileset.

            // Cache file records in case client cache is outdated and all
            // records must be returned including the hash (not calculated yet)

            // Note: if a file or directory is deleted, the modification time
            // of the directory containing the file or directory is updated so
            // deletions do trigger a cache invalidation. The root directory is
            // included in the file list for this reason.

            long lastModified = -1;
            Collection<FileRecord> fileRecords = new ArrayList();

            String cacheDir = new File(FileHashCache.getCacheDir(theFileset.getName())).getCanonicalPath();

            log.info("traverse " + getLocalSubPath());

            long startTime = System.currentTimeMillis();
            for(FileRecord fr: FileRecord.getFileRecordsInDir(getLocalSubPath(), regexp, noRegexpMatches)) {
                if(fr.getFile().getCanonicalPath().startsWith(cacheDir)) {
                    continue;
                }
                fileRecords.add(fr);
                lastModified = Math.max(lastModified, fr.getLastModified());
            }
            log.info(String.format("traversed %d files and dirs%s", fileRecords.size(), regexp == null ? "" : ", filtered " + noRegexpMatches + " by regexp"));
            String time = DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, false);
            if(ifModifiedSince >= lastModified) {
                log.info("not modified since " + dateToString(new Date(lastModified)) + ", took " + time);
                return new ErrorResolution(HttpServletResponse.SC_NOT_MODIFIED);
            } else {
                log.info("last modified date " + dateToString(new Date(lastModified)) + " later than client date of " + dateToString(new Date(ifModifiedSince)) + ", returning list (time " + time + ")");
                // Avoid walking dirs twice, only calculate hashes
                return new FilesetListingResolution(theFileset, fileRecords);
            }
        }
    }
}
