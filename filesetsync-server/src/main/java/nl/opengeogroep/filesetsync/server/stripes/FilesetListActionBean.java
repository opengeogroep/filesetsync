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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.After;
import net.sourceforge.stripes.action.ErrorResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.controller.LifecycleStage;
import nl.opengeogroep.filesetsync.FileRecord;
import static nl.opengeogroep.filesetsync.FileRecord.TYPE_FILE;
import nl.opengeogroep.filesetsync.Protocol;
import static nl.opengeogroep.filesetsync.Protocol.FILELIST_ENCODING;
import static nl.opengeogroep.filesetsync.util.FormatUtil.dateToString;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.MDC;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/fileset/list/{filesetName}")
public class FilesetListActionBean extends FilesetBaseActionBean {

    private static final Log log = LogFactory.getLog("api.list");

    private ActionBeanContext context;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    private static class FilesetListingResolution extends StreamingResolution {

        private final Iterable<FileRecord> iterable;

        private final long startTime;
        private long totalBytes;
        private int dirs, files;

        public FilesetListingResolution(Iterable<FileRecord> iterable) {
            super("text/plain, encoding=" + FILELIST_ENCODING);
            this.iterable = iterable;

            startTime = System.currentTimeMillis();
        }

        @Override
        public void stream(HttpServletResponse response) throws IOException  {
            final Protocol.BufferedFileRecordEncoder encoder = new Protocol.BufferedFileRecordEncoder(response.getOutputStream());

            MutableLong hashTime = new MutableLong();
            for(FileRecord fr: iterable) {
                if(TYPE_FILE == fr.getType()) {
                    fr.calculateHash(hashTime);
                    files++;
                    totalBytes += fr.getSize();
                } else {
                    dirs++;
                }
                encoder.write(fr);
            }

            log.info(String.format("returned %d directories and %d files, total size %d KB, time %s, hash time %s, hash speed %s",
                    dirs,
                    files,
                    totalBytes / 1024,
                    DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, false),
                    DurationFormatUtils.formatDurationWords(hashTime.getValue(), true, false),
                    (hashTime.getValue() < 100 ? "n/a" : Math.round(totalBytes / 1024.0 / (hashTime.getValue() / 1000.0)) + " KB/s")
            ));
            encoder.flush();
        }
    }

    public Resolution list() throws Exception {
        MDC.put("fileset", getFileset().getName());

        long ifModifiedSince = getContext().getRequest().getDateHeader("If-Modified-Since");

        if(ifModifiedSince == -1) {
            log.trace("begin directory traversal from " + getFileset().getPath());
            // Stream file records directly to output without buffering all
            // records in memory
            return new FilesetListingResolution(getFileset().getFileRecords());
        } else {
            // A conditional HTTP request with If-Modified-Since is checked
            // against the latest last modified date of all the files and
            // directories in the fileset.

            // Cache file records in case client cache is outdated and all
            // records must be returned including the hash (not calculated yet)

            // Ideally the fileset is cached once (in memory, a file listing of
            // a million files is in the order of ~25Mb) including the
            // checksums and is invalidated by watching the directory using
            // a Commons-IO FileAlterationObserver or Java 7 WatchService:
            // http://docs.oracle.com/javase/tutorial/essential/io/notification.html
            // The performance with filesets with a lot of files and lots of
            // updates during a re-seeding is of course an issue.

            long lastModified = -1;
            Collection<FileRecord> fileRecords = new ArrayList();

            log.trace("begin directory traversal for conditial http request from " + getFileset().getPath());
            for(FileRecord fr: getFileset().getFileRecords()) {
                fileRecords.add(fr);
                lastModified = Math.max(lastModified, fr.getLastModified());
            }

            if(ifModifiedSince >= lastModified) {
                log.info("not modified since " + dateToString(new Date(lastModified)));
                return new ErrorResolution(HttpServletResponse.SC_NOT_MODIFIED);
            } else {
                log.info("last modified date " + dateToString(new Date(lastModified)) + " later than client date of " + dateToString(new Date(lastModified)) + ", returning list");
                // Avoid walking dirs twice, only calculate hashes
                return new FilesetListingResolution(fileRecords);
            }
        }
    }

    @After(stages = LifecycleStage.ResolutionExecution)
    public void after() {
        log.trace("response sent");
    }
}
