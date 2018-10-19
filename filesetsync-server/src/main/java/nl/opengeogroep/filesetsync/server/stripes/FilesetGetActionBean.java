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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.filesetsync.FileRecord;
import nl.opengeogroep.filesetsync.protocol.MultiFileEncoder;
import static nl.opengeogroep.filesetsync.protocol.MultiFileEncoder.MULTIFILE_MIME_TYPE;
import nl.opengeogroep.filesetsync.protocol.Protocol;
import static nl.opengeogroep.filesetsync.protocol.Protocol.FILELIST_MIME_TYPE;
import static nl.opengeogroep.filesetsync.protocol.Protocol.FILELIST_V2_MIME_TYPE;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/fileset/get/{filesetPath}")
public class FilesetGetActionBean extends FilesetBaseActionBean {
    private static final Log log = LogFactory.getLog("api.get");

    @Override
    protected final String getLogName() {
        return "api.get";
    }

    public Resolution get() throws Exception {
        String logPrefix = getFilesetName() + (getSubPath().length() != 0 ? getSubPath() : "") + " get:";

        Iterable<FileRecord> filesToStream;

        boolean isFileList = false;
        int version = 1;
        String contentType = getContext().getRequest().getContentType();
        if(FILELIST_MIME_TYPE.equals(contentType)) {
            isFileList = true;
        } else if(FILELIST_V2_MIME_TYPE.equals(contentType)) {
            isFileList = true;
            version = 2;
        }
        if(isFileList) {

            InputStream in = getContext().getRequest().getInputStream();
            if("gzip".equals(getContext().getRequest().getHeader(HttpHeaders.CONTENT_ENCODING))) {
                in = new GZIPInputStream(in);
            }
            List<FileRecord> requestedFiles = Protocol.decodeFilelist(in);
            long totalSize = 0;
            int unacceptableFiles = 0;
            String canonicalFilesetPath = new File(getFileset().getPath()).getCanonicalPath();
            for(FileRecord fr: requestedFiles) {
                File localFile = new File(getLocalSubPath() + File.separator + fr.getName()).getCanonicalFile();

                if(!localFile.getCanonicalPath().startsWith(canonicalFilesetPath)) {
                    unacceptableFiles++;
                    log.warn("unacceptable file: not under fileset path: " + fr.getName());
                    continue;
                } else if(!localFile.exists() || !((localFile.isFile() && localFile.canRead()) || localFile.isDirectory())) {
                    unacceptableFiles++;
                    log.warn("unacceptable file: not existing, not a readable file or not a directory: " + fr.getName());
                    continue;
                }
                fr.setFile(localFile);
                if(localFile.isFile()) {
                    totalSize += localFile.length();
                }
            }
            log.info(String.format("%s streaming %d files (total %.0f KB)%s",
                    logPrefix,
                    requestedFiles.size(),
                    totalSize / 1024.0,
                    unacceptableFiles != 0 ? ", removed " + unacceptableFiles + " unacceptable requested files" : ""));
            filesToStream = requestedFiles;
        } else {
            // check if path is single file
            final File f = new File(getLocalSubPath());
            if(f.isFile()) {
                return new StreamingResolution("application/octet-stream", new FileInputStream(f)) {
                    @Override
                    protected void applyHeaders(HttpServletResponse response) {
                        super.applyHeaders(response);
                        response.setContentLength((int)f.length());
                    }
                };
            } else if(!f.isDirectory()) {
                return new ErrorMessageResolution("Error: path is not a file or directory");
            }

            log.info(logPrefix + " recursively streaming ");
            filesToStream = FileRecord.getFileRecordsInDir(getLocalSubPath(), null, new MutableInt());
        }

        return new MultiFileStreamingResolution(logPrefix, filesToStream, version);
    }

    private class MultiFileStreamingResolution extends StreamingResolution {
        private final Iterable<FileRecord> fileRecords;
        private final String logPrefix;
        private final int version;

        public MultiFileStreamingResolution(String logPrefix, Iterable<FileRecord> fileRecords, int version) {
            super(MULTIFILE_MIME_TYPE);
            this.logPrefix = logPrefix;
            this.fileRecords = fileRecords;
            this.version = version;
        }

        @Override
        public void stream(HttpServletResponse response) throws IOException {
            String acceptEncoding = getContext().getRequest().getHeader("Accept-Encoding");

            CountingOutputStream compressedCounter;
            CountingOutputStream uncompressedCounter;
            if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                response.setHeader("Content-Encoding", "gzip");
                compressedCounter = new CountingOutputStream(response.getOutputStream());
                uncompressedCounter = new CountingOutputStream(new GZIPOutputStream(compressedCounter));
            } else {
                compressedCounter = new CountingOutputStream(response.getOutputStream());
                uncompressedCounter = compressedCounter;
            }

            final MultiFileEncoder streamer = new MultiFileEncoder(uncompressedCounter, version, log);

            long startTime = System.currentTimeMillis();
            try {
                for(FileRecord fr: fileRecords) {
                    try {
                        streamer.write(fr);
                    } catch(Exception e) {
                        if(e.getClass().getName().endsWith("ClientAbortException")) {
                            log.warn("received client abort streaming " + fr);
                        } else {
                            log.error("exception streaming " + fr, e);
                        }
                        return;
                    }
                }
            } finally {
                try {
                    streamer.close();
                } catch(IOException e) {
                }
                IOUtils.closeQuietly(uncompressedCounter);

                long compressedBytes = compressedCounter.getByteCount();
                long uncompressedBytes = uncompressedCounter.getByteCount();
                long duration = System.currentTimeMillis() - startTime;
                log.info(String.format("%s streamed %d KB (uncompressed %d KB, ratio %.1f%%) in %s%s",
                        logPrefix,
                        compressedBytes / 1024,
                        uncompressedBytes / 1024,
                        Math.abs(100-(100.0/uncompressedBytes*compressedBytes)),
                        DurationFormatUtils.formatDurationWords(duration, true, false),
                        (duration < 100 ? "" : ", " + Math.round(compressedBytes / 1024.0 / (duration / 1000.0)) + " KB/s")
                ));
            }
        }
    }
}
