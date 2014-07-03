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
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.After;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.controller.LifecycleStage;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.filesetsync.FileRecord;
import nl.opengeogroep.filesetsync.Protocol;
import static nl.opengeogroep.filesetsync.Protocol.FILELIST_MIME_TYPE;
import static nl.opengeogroep.filesetsync.Protocol.MULTIFILE_MIME_TYPE;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

    private ActionBeanContext context;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Resolution get() throws Exception {

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

        Iterable<FileRecord> filesToStream;

        if(FILELIST_MIME_TYPE.equals(context.getRequest().getContentType())) {
            List<FileRecord> requestedFiles = Protocol.decodeFilelist(context.getRequest().getInputStream());
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
            log.info(String.format("streaming %d files (total %d bytes), removed %d unacceptable requested files",
                    requestedFiles.size(),
                    totalSize,
                    unacceptableFiles));
            filesToStream = requestedFiles;
        } else {
            log.info("recursively streaming all files walking from directory " + getLocalSubPath());
            filesToStream = FileRecord.getFileRecordsInDir(getLocalSubPath());
        }

        return new MultiFileStreamingResolution(filesToStream);
    }

    private class MultiFileStreamingResolution extends StreamingResolution {
        final private Iterable<FileRecord> fileRecords;

        public MultiFileStreamingResolution(Iterable<FileRecord> fileRecords) {
            super(MULTIFILE_MIME_TYPE);
            this.fileRecords = fileRecords;
        }

        @Override
        public void stream(HttpServletResponse response) throws IOException {
            response.setContentType(FILELIST_MIME_TYPE);

            String acceptEncoding = getContext().getRequest().getHeader("Accept-Encoding");

            OutputStream out;
            if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                response.setHeader("Content-Encoding", "gzip");
                out = new GZIPOutputStream(response.getOutputStream());
            } else {
                out = response.getOutputStream();
            }

            final Protocol.MultiFileEncoder streamer = new Protocol.MultiFileEncoder(out, log);

            long startTime = System.currentTimeMillis();
            long totalBytes = 0;
            for(FileRecord fr: fileRecords) {
                try {
                    streamer.write(fr);
                    totalBytes += fr.getSize();
                } catch(Exception e) {
                    if(e.getClass().getName().endsWith("ClientAbortException")) {
                        log.warn("received client abort streaming " + fr);
                    } else {
                        log.error("exception streaming " + fr, e);
                    }
                }
            }
            streamer.close();
            out.close();
            long duration = System.currentTimeMillis() - startTime;
            log.info(String.format("streamed %d KB in time %s, %s",
                    totalBytes / 1024,
                    DurationFormatUtils.formatDurationWords(duration, true, false),
                    (duration < 100 ? "n/a" : Math.round(totalBytes / 1024.0 / (duration / 1000.0)) + " KB/s")
            ));

        }
    }

    @After(stages = LifecycleStage.ResolutionExecution)
    public void after() {
        log.trace("response sent");
    }
}
