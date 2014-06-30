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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import nl.opengeogroep.filesetsync.FileRecord;
import nl.opengeogroep.filesetsync.Protocol;
import static nl.opengeogroep.filesetsync.Protocol.FILELIST_ENCODING;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.MDC;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/fileset/{filesetName}/list")
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

        private final File startDir;
        private final String rootPath;
        private final long startTime;
        private long hashTime, totalBytes;
        private int dirs, files;

        public FilesetListingResolution(File startDir) {
            super("text/plain, encoding=" + FILELIST_ENCODING);
            this.startDir = startDir;
            this.rootPath = startDir.getAbsolutePath();

            log.trace("begin directory traversal from " + rootPath);
            startTime = System.currentTimeMillis();
        }

        @Override
        public void stream(HttpServletResponse response) throws IOException  {
            final Protocol.BufferedFileRecordEncoder encoder = new Protocol.BufferedFileRecordEncoder(response.getOutputStream());

            Iterator<File> it = FileUtils.iterateFilesAndDirs(startDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
            while(it.hasNext()) {
                File f = it.next();
                if(!f.equals(startDir)) {
                    String relativePath = f.getAbsolutePath().substring(rootPath.length()+1);
                    FileRecord fr = new FileRecord(f, relativePath);
                    if(f.isFile()) {
                        long startTimeHash = System.currentTimeMillis();
                        fr.calculateHash(f);
                        hashTime += System.currentTimeMillis() - startTimeHash;
                        files++;
                        totalBytes += fr.getSize();
                    } else {
                        dirs++;
                    }
                    encoder.write(fr);
                }
            }

            log.info(String.format("returned %d directories and %d files, total size %d KB, time %s, hash time %s, hash speed %s",
                    dirs,
                    files,
                    totalBytes / 1024,
                    DurationFormatUtils.formatPeriodISO(startTime, System.currentTimeMillis()),
                    DurationFormatUtils.formatDurationISO(hashTime),
                    (hashTime == 0 ? "n/a" : Math.round(totalBytes / 1024.0 / (hashTime / 1000.0)) + " KB/s")
            ));
            encoder.flush();
        }
    }

    public Resolution list() throws Exception {
        MDC.put("fileset", getFileset().getName());

        final File filesetFile = new File(getFileset().getPath());

        if(filesetFile.isFile()) {
            // Don't check for conditional HTTP request
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Protocol.BufferedFileRecordEncoder encoder = new Protocol.BufferedFileRecordEncoder(bos);
            FileRecord fr = new FileRecord(filesetFile, filesetFile.getName());
            fr.calculateHash(filesetFile);
            encoder.write(fr);
            encoder.flush();
            return new StreamingResolution("text/plain, encoding=" + FILELIST_ENCODING, new ByteArrayInputStream(bos.toByteArray()));
        } else {

            // A conditional HTTP request with If-Modified-Since should be
            // checked against the latest last modified dates of all the files
            // and directories in the fileset.

            // For a fileset with millions of files checking all of them can be
            // a resource drain. If the fileset is modified a second traversal
            // is needed to calculate all checksums.

            // Ideally the fileset is cached (in memory, a file listing of
            // a million files is in the order of ~25Mb) including the
            // checksums and is invalidated by watching the directory using
            // a Commons-IO FileAlterationObserver or Java 7 WatchService:
            // http://docs.oracle.com/javase/tutorial/essential/io/notification.html
            // The performance with filesets with a lot of files and lots of
            // updates during a re-seeding is of course an issue.

            return new FilesetListingResolution(filesetFile);
        }
    }
}
