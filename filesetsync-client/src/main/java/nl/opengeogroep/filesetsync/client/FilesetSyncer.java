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

package nl.opengeogroep.filesetsync.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import nl.opengeogroep.filesetsync.FileRecord;
import nl.opengeogroep.filesetsync.Protocol;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.client.util.HttpClientUtil;
import nl.opengeogroep.filesetsync.util.FormatUtil;
import static nl.opengeogroep.filesetsync.util.FormatUtil.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import static org.apache.http.HttpStatus.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

/**
 *
 * @author Matthijs Laan
 */
public class FilesetSyncer {
    private static final Log log = LogFactory.getLog(FilesetSyncer.class);

    private final SyncJobState state;

    private final Fileset fs;

    private String serverUrl;

    public FilesetSyncer(Fileset fs) {
        this.fs = fs;
        state = SyncJobStatePersistence.getInstance().getState(fs.getName(), true);
    }

    public void sync() {
        log.info("Starting sync for job with name " + fs.getName());
        log.info(fs);
        log.info(String.format("Last started %s and finished %s",
                state.getLastRun() == null ? "never" : "at " + dateToString(state.getLastRun()),
                state.getLastFinished() == null ? "never" : "at " + dateToString(state.getLastFinished())));

        state.startNewRun();

        serverUrl = fs.getServer();
        if(!serverUrl.endsWith("/")) {
            serverUrl += "/";
        }
        serverUrl += "fileset/";

        try {
            retrieveFilesetList();
            compareFilesetList();
            transferFiles();

            log.info("Sync job complete");
            state.endRun(true);
        } catch(Exception e) {
            // TODO: set retry status

            log.error("Sync job encountered exception", e);
            state.endRun(false);
        }
    }

    private void action(String s) {
        state.setCurrentAction(s);
        log.info(s);
    }

    private void retrieveFilesetList() throws IOException {

        final boolean cachedFileList = state.getFileList() != null && state.getFileListDate() != null;

        String s = "Retrieving file list";
        if(cachedFileList) {
            s += String.format(" (last file list with %d files and directories cached at %s)",
                    state.getFileList().size(),
                    FormatUtil.dateToString(state.getFileListDate()));
        }
        action(s);

        try(CloseableHttpClient httpClient = HttpClientUtil.get()) {
            HttpGet get = new HttpGet(serverUrl + "list/" + fs.getRemote());
            if(cachedFileList) {
                get.addHeader(HttpHeaders.IF_MODIFIED_SINCE, HttpClientUtil.format(state.getFileListDate()));
            }
            // Request poorly encoded text format
            get.addHeader(HttpHeaders.ACCEPT, "text/plain");

            ResponseHandler<List<FileRecord>> rh = new ResponseHandler<List<FileRecord>>() {
                @Override
                public List handleResponse(HttpResponse hr) throws ClientProtocolException, IOException {

                    int status = hr.getStatusLine().getStatusCode();
                    if(status == SC_NOT_MODIFIED) {
                        return null;
                    } else if(status >= SC_OK && status < 300) {
                        HttpEntity entity = hr.getEntity();
                        if(entity == null) {
                            throw new ClientProtocolException("No response entity, invalid server URL?");
                        }
                        try(InputStream in = entity.getContent()) {
                            return Protocol.decodeFilelist(in);
                        }
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + hr.getStatusLine());
                    }
                }
            };

            List<FileRecord> fileList = httpClient.execute(get, rh);

            if(fileList == null) {
                log.info("Cached file list is up-to-date");
            } else {
                log.info("Filelist returned " + fileList.size() + " files");

                /* Calculate last modified client-side, requires less server
                 * memory
                 */

                long lastModified = -1;
                for(FileRecord fr: fileList) {
                    lastModified = Math.max(lastModified, fr.getLastModified());
                }
                if(lastModified != -1) {
                    state.setFileList(fileList);
                    state.setFileListDate(new Date(lastModified));
                    SyncJobStatePersistence.persist();
                }

                for(FileRecord fr: fileList) {
                    log.info(fr);
                }
            }
        }
    }

    private void compareFilesetList() {
    }

    private void transferFiles() {
    }
}
