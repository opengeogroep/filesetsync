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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import nl.opengeogroep.filesetsync.FileRecord;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import nl.opengeogroep.filesetsync.client.util.HttpClientUtil;
import nl.opengeogroep.filesetsync.protocol.BufferedFileListEncoder;
import nl.opengeogroep.filesetsync.protocol.MultiFileDecoder;
import nl.opengeogroep.filesetsync.protocol.MultiFileHeader;
import nl.opengeogroep.filesetsync.protocol.Protocol;
import nl.opengeogroep.filesetsync.util.FormatUtil;
import static nl.opengeogroep.filesetsync.util.FormatUtil.*;
import nl.opengeogroep.filesetsync.util.HttpUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import static org.apache.http.HttpStatus.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

/**
 *
 * @author Matthijs Laan
 */
public class FilesetSyncer {
    private static final Log log = LogFactory.getLog(FilesetSyncer.class);

    private final SyncJobState state;

    private final Fileset fs;

    private String serverUrl;

    List<FileRecord> fileList;
    
    long totalBytes;

    String localCanonicalPath;
    
    public FilesetSyncer(Fileset fs) throws IOException {
        this.fs = fs;
        localCanonicalPath = new File(fs.getLocal()).getCanonicalPath();
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

        final boolean cachedFileList = state.getFileListDate() != null
                && state.getFileListRemotePath().equals(fs.getRemote())
                && SyncJobState.haveCachedFileList(fs.getName());

        String s = "Retrieving file list";
        if(cachedFileList) {
            s += String.format(" (last file list cached at %s)", FormatUtil.dateToString(state.getFileListDate()));
        }
        action(s);

        try(CloseableHttpClient httpClient = HttpClientUtil.get()) {
            HttpGet get = new HttpGet(serverUrl + "list/" + fs.getRemote());
            if(cachedFileList) {
                get.addHeader(HttpHeaders.IF_MODIFIED_SINCE, HttpUtil.formatDate(state.getFileListDate()));
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
                        String entity = hr.getEntity() == null ? null : EntityUtils.toString(hr.getEntity());
                        throw new ClientProtocolException("Unexpected response status: " + hr.getStatusLine() + ",  body: " + entity);
                    }
                }
            };

            fileList = httpClient.execute(get, rh);

            if(fileList == null) {
                log.info("Cached file list is up-to-date");

                fileList = SyncJobState.readCachedFileList(fs.getName());
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
                    state.setFileListRemotePath(fs.getRemote());
                    state.setFileListDate(new Date(lastModified));
                    SyncJobState.writeCachedFileList(fs.getName(), fileList);
                    SyncJobStatePersistence.persist();
                }
            }
            log.info("Filelist: " + fileList.size() + " entries");
        }
    }

    private void compareFilesetList() {
        // TODO
    }

    private void transferFiles() throws IOException {
        if(fileList.isEmpty()) {
            log.info("no files to transfer");
            return;
        }

        action(String.format("transferring %d files", fileList.size()));

        // A "chunk" here means a set of multiple files not smaller than the
        // configured chunk size (unless there are no more files)

        long chunks = 0;
        totalBytes = 0;
        long chunkSize = FormatUtil.parseByteSize(SyncConfig.getInstance().getProperty("chunkSize","5M"));

        int index = 0;
        int endIndex;
        do {
            int thisChunkSize = 0;
            endIndex = fileList.size()-1;
            for(int j = index; j < fileList.size(); j++) {
                thisChunkSize += fileList.get(j).getSize();
                if(thisChunkSize >= chunkSize) {
                    endIndex = j;
                    break;
                }
            }
            List<FileRecord> chunkList = fileList.subList(index, endIndex+1);
            log.info(String.format("requesting chunk of %d files (size %d KB)", chunkList.size(), thisChunkSize));
            if(log.isTraceEnabled()) {
                int t = 0;
                for(FileRecord fr: chunkList) {
                    log.trace(String.format("#%3d: %8d bytes: %s", ++t, fr.getSize(), fr.getName()));
                }
            }
            transferChunk(chunkList);
            index = endIndex+1;
            chunks++;
        } while(endIndex < fileList.size()-1);

        log.info(String.format("transfer complete, %d chunks, %d KB total", chunks, totalBytes/1024));
    }

    private void transferChunk(List<FileRecord> chunkList) throws IOException {
        try(CloseableHttpClient httpClient = HttpClientUtil.get()) {
            HttpPost post = new HttpPost(serverUrl + "get/" + fs.getRemote());

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            new BufferedFileListEncoder(b).writeAll(chunkList).close();

            post.setEntity(new ByteArrayEntity(b.toByteArray()));
            post.setHeader(HttpHeaders.CONTENT_TYPE, Protocol.FILELIST_MIME_TYPE);

            log.info("Request: " + post.getRequestLine());
            try(CloseableHttpResponse response = httpClient.execute(post)) {
                log.info("Response: " + response.getStatusLine());
                
                int status = response.getStatusLine().getStatusCode();
                if(status < 200 || status >= 300) {
                    throw new IOException(String.format("Server returned \"%s\" for request \"%s\", body: %s", 
                            response.getStatusLine(), 
                            post.getRequestLine(), 
                            EntityUtils.toString(response.getEntity())));
                }
                
                try(MultiFileDecoder decoder = new MultiFileDecoder(response.getEntity().getContent())) {
                    int i = 0;
                    for(MultiFileHeader mfh: decoder) {
                        log.info(String.format("File #%3d: %8d bytes, %s, %s", ++i, mfh.getContentLength(), mfh.getContentType(), mfh.getFilename()));
                        File local;
                        if(mfh.getFilename().equals(".")) {
                            if(mfh.isDirectory()) {
                                // skip root directory
                                continue;
                            } else {
                                // single file sync, write to local file
                                local = new File(fs.getLocal());
                            }
                        } else {
                            local = new File(fs.getLocal() + File.separator + mfh.getFilename());
                            // Detect if server tries to overwrite file in parent of local path
                            if(!local.getCanonicalPath().startsWith(localCanonicalPath)) {
                                throw new IOException("Server returned invalid filename: " + mfh.getFilename());
                            }
                        }
                        
                        if(mfh.isDirectory()) {
                            log.info("Creating directory " + local.getName());
                            local.mkdirs();
                            try {
                                local.setLastModified(mfh.getLastModified().getTime());
                            } catch(Exception e) {
                                log.warn("Exception setting last modified time of " + local.getName() + ": " + ExceptionUtils.getMessage(e));
                            }                            
                            continue;
                        }

                        if(local.exists()) {
                            log.info("Overwriting local file " + local.getName());
                        } else {
                            log.info("Writing new file " + local.getName());
                        }
                        try(FileOutputStream out = new FileOutputStream(local)) {
                            IOUtils.copy(mfh.getBody(), out);
                            totalBytes += mfh.getContentLength();
                        } catch(IOException e) {
                            log.error(String.format("Error writing to local file \"%s\": %s", fs.getLocal(), ExceptionUtils.getMessage(e)));
                            throw e;
                        }
                        try {
                            local.setLastModified(mfh.getLastModified().getTime());
                        } catch(Exception e) {
                            log.warn("Exception setting last modified time of " + local.getName() + ": " + ExceptionUtils.getMessage(e));
                        }
                    }
                }
            }
        }
    }
}
