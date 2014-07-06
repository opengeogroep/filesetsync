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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import nl.opengeogroep.filesetsync.FileRecord;
import static nl.opengeogroep.filesetsync.FileRecord.TYPE_DIRECTORY;
import static nl.opengeogroep.filesetsync.FileRecord.TYPE_FILE;
import nl.opengeogroep.filesetsync.FileRecordListDirectoryIterator;
import static nl.opengeogroep.filesetsync.client.SyncJobState.*;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import static org.apache.http.HttpStatus.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
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

    final private List<Pair<File,Long>> directoriesLastModifiedTimes = new ArrayList();

    final private Map<String,String> localFilesByHash = new HashMap();

    public FilesetSyncer(Fileset fs) {
        SyncJobStatePersistence.setCurrentFileset(fs);
        this.fs = fs;
        try {
            localCanonicalPath = new File(fs.getLocal()).getCanonicalPath();
        } catch(IOException e) {
            log.error("Error determining local canonical path", e);
        }
        state = SyncJobStatePersistence.getInstance().getState(fs.getName(), true);
    }

    public void sync() {
        if(localCanonicalPath == null) {
            state.endRun(STATE_ERROR);
            return;
        }

        log.info(String.format("Starting sync for job \"%s\", last started %s and finished %s",
                fs.getName(),
                state.getLastRun() == null ? "never" : "at " + dateToString(state.getLastRun()),
                state.getLastFinished() == null ? "never" : "at " + dateToString(state.getLastFinished())));
        log.trace(fs);

        state.startNewRun();

        serverUrl = fs.getServer();
        if(!serverUrl.endsWith("/")) {
            serverUrl += "/";
        }
        serverUrl += "fileset/";

        try {
            retrieveFilesetList();
            if(fs.isDelete()) {
                deleteLocalFiles();
            }
            compareFilesetList();
            transferFiles();
            setDirectoriesLastModified();

            log.info("Sync job complete");
            state.endRun(STATE_COMPLETED);
        } catch(IOException e) {
            state.setFailedTries(state.getFailedTries()+1);

            log.error("Exception during sync job: " + ExceptionUtils.getMessage(e));
            log.trace("Full stack trace", e);
            if(state.getFailedTries() >= fs.getMaxTries()) {
                log.error("Retryable IOException but max tries reached after " + state.getFailedTries() + " times, fatal error");
                state.endRun(STATE_ERROR);
            } else {
                log.error(String.format("IO exception, retrying later after %d minutes (try %d of max %d)", fs.getRetryWaitTime(), state.getFailedTries(), fs.getMaxTries()));
                state.endRun(STATE_RETRY);
            }
        } finally {
            SyncJobStatePersistence.setCurrentFileset(null);
        }
    }

    private void action(String s) {
        state.setCurrentAction(s);
        log.info(s);
    }

    private void retrieveFilesetList() throws IOException {

        final boolean cachedFileList = state.getFileListDate() != null
                && state.getFileListRemotePath().equals(fs.getRemote())
                && (!fs.isHash() || state.isFileListHashed())
                && SyncJobState.haveCachedFileList(fs.getName());

        String s = "Retrieving file list";
        if(cachedFileList) {
            s += String.format(" (last file list cached at %s)", FormatUtil.dateToString(state.getFileListDate()));
        }
        action(s);

        try(CloseableHttpClient httpClient = HttpClientUtil.get()) {
            HttpUriRequest get = RequestBuilder.get()
                    .setUri(serverUrl + "list/" + fs.getRemote())
                    .addParameter("hash", fs.isHash() + "")
                    .build();
            if(cachedFileList) {
                get.addHeader(HttpHeaders.IF_MODIFIED_SINCE, HttpUtil.formatDate(state.getFileListDate()));
            }
            // Request poorly encoded text format
            get.addHeader(HttpHeaders.ACCEPT, "text/plain");

            ResponseHandler<List<FileRecord>> rh = new ResponseHandler<List<FileRecord>>() {
                @Override
                public List handleResponse(HttpResponse hr) throws ClientProtocolException, IOException {
                    log.info("< " + hr.getStatusLine());

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
                        if(log.isTraceEnabled()) {
                            String entity = hr.getEntity() == null ? null : EntityUtils.toString(hr.getEntity());
                            log.trace("Response body: " + entity);
                        }
                        throw new ClientProtocolException("Server error: " + hr.getStatusLine());
                    }
                }
            };

            log.info("> " + get.getRequestLine());
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
                    state.setFileListHashed(fs.isHash());
                    SyncJobState.writeCachedFileList(fs.getName(), fileList);
                    SyncJobStatePersistence.persist();
                }
            }
        }
    }

    private void deleteLocalFiles() {
        if(Shutdown.isHappening()) {
            return;
        }

        for(List<FileRecord> dirList: new FileRecordListDirectoryIterator(fileList)) {
            Iterator<FileRecord> it = dirList.iterator();
            FileRecord dir = it.next();
            File localDir = new File(fs.getLocal() + File.separator + dir.getName());

            if(!localDir.exists()) {
                continue;
            }

            List<String> toDelete = new ArrayList(Arrays.asList(localDir.list()));

            while(it.hasNext()) {
                FileRecord fr = it.next();
                String name = dir.getName().equals(".") ? fr.getName() : fr.getName().substring(dir.getName().length()+1);
                if(toDelete.indexOf(name) != -1) {
                    // Don't delete this file -- may need to be overwritten though

                    try {
                        // But if is not the same type, do delete it
                        char localType = new File(localDir.getCanonicalPath() + File.separator + name).isDirectory()
                                    ? 'd'
                                    : 'f';
                        if(localType == fr.getType()) {
                            toDelete.remove(name);
                        }
                    } catch (IOException ex) {
                    }
                }
            }

            for(String deleteIt: toDelete) {
                if(Shutdown.isHappening()) {
                    return;
                }

                File f = new File(localDir + File.separator + deleteIt);
                try {
                    if(f.isDirectory()) {
                        log.info("rmdirs    " + f.getCanonicalPath());
                        FileUtils.deleteDirectory(f);
                    } else {
                        log.info("delete    " + f.getCanonicalPath());
                        f.delete();
                    }
                } catch(Exception e) {
                    log.error("Exception deleting file " + f + ": " + ExceptionUtils.getMessage(e));
                }
            }
        }
    }

    /**
     * Removes files which are locally up-to-date from the list of files to
     * transfer. Updates lastModified date.
     */
    private void compareFilesetList() throws IOException {

        List<FileRecord> alreadyLocal = new ArrayList();

        MutableLong hashTime = new MutableLong();
        long hashBytes = 0;
        long startTime = System.currentTimeMillis();

        for(FileRecord fr: fileList) {
            if(Shutdown.isHappening()) {
                return;
            }

            File localFile = new File(fs.getLocal() + File.separator + fr.getName());
            if(fr.getType() == TYPE_DIRECTORY && localFile.exists()) {
                if(!localFile.isDirectory()) {
                    log.error("Local file in is the way for remote directory: " + localFile.getCanonicalPath());
                }
                if(fr.getLastModified() != localFile.lastModified()) {
                    log.info(String.format("Later updating last modified for directory %s", localFile.getCanonicalPath()));
                    directoriesLastModifiedTimes.add(Pair.of(localFile, fr.getLastModified()));
                }
                alreadyLocal.add(fr);
            }
            if(fr.getType() == TYPE_FILE && localFile.exists()) {
                if(!localFile.isFile()) {
                    log.error("Local non-file is in the way for remote file: " + localFile.getCanonicalPath());
                }
                if(fs.isHash()) {
                    try {
                        String hash = FileRecord.calculateHash(localFile, hashTime);
                        localFilesByHash.put(hash, localFile.getCanonicalPath());
                        hashBytes += localFile.length();
                        if(hash.equals(fr.getHash())) {
                            log.trace("Same hash for " + fr.getName());
                            if(fr.getLastModified() > localFile.lastModified()) {
                                log.trace("Same hash, updating last modified for " + fr.getName());
                                localFile.setLastModified(fr.getLastModified());
                            }
                            alreadyLocal.add(fr);
                        } else {
                            log.trace("Hash mismatch for " + fr.getName());
                        }
                    } catch(Exception e) {
                        log.error("Error hashing " + localFile.getCanonicalPath() + ": " + ExceptionUtils.getMessage(e));
                    }
                } else {
                    if(fr.getLastModified() > localFile.lastModified()) {
                        log.trace("Remote file newer: " + fr.getName());
                    } else if(fr.getLastModified() < localFile.lastModified()) {
                        log.warn(String.format("Keeping local file last modified at %s, later than remote file at %s: ", dateToString(new Date(localFile.lastModified())), dateToString(new Date(fr.getLastModified())), fr.getName()));
                        alreadyLocal.add(fr);
                    } else {
                        log.trace("Local file unmodified: " + fr.getName());
                        alreadyLocal.add(fr);
                    }
                }
            }
        }

        // TODO: if file in file list already in localFilesByHash OR, remove them
        // Also remove duplicate hashes in fileList

        String hashInfo;
        if(fs.isHash()) {
            hashInfo = String.format(", hashed hashed %d KB, hash speed %s",
                    hashBytes / 1024,
                    (hashTime.getValue() < 100 ? "n/a" : Math.round(hashBytes / 1024.0 / (hashTime.getValue() / 1000.0)) + " KB/s"));
        } else {
            hashInfo = "";
        }
        log.info(String.format("Compared file list to local files in %s, %d files up-to-date%s",
                DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, false),
                alreadyLocal.size(),
                hashInfo));
        fileList.removeAll(alreadyLocal);
    }

    private void transferFiles() throws IOException {
        if(Shutdown.isHappening()) {
            return;
        }

        if(fileList.isEmpty()) {
            log.info("No files to transfer");
            return;
        }

        action(String.format("Transferring %d files", fileList.size()));

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
            log.info(String.format("Requesting chunk of %d files (size %d KB)", chunkList.size(), thisChunkSize));
            if(log.isTraceEnabled()) {
                int t = 0;
                for(FileRecord fr: chunkList) {
                    log.trace(String.format("#%3d: %8d bytes: %s", ++t, fr.getSize(), fr.getName()));
                }
            }
            transferChunk(chunkList);
            if(Shutdown.isHappening()) {
                return;
            }

            index = endIndex+1;
            chunks++;
        } while(endIndex < fileList.size()-1);

        log.info(String.format("Transfer complete, %d chunks, %d KB total", chunks, totalBytes/1024));
    }

    private void setDirectoriesLastModified() {
        if(!directoriesLastModifiedTimes.isEmpty()) {
            log.info(String.format("Setting last modified times of %d directories...", directoriesLastModifiedTimes.size()));
            for(Pair<File,Long> dlm: directoriesLastModifiedTimes) {
                dlm.getLeft().setLastModified(dlm.getRight());
            }
        }
    }

    private void transferChunk(List<FileRecord> chunkList) throws IOException {
        try(CloseableHttpClient httpClient = HttpClientUtil.get()) {
            HttpPost post = new HttpPost(serverUrl + "get/" + fs.getRemote());

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            new BufferedFileListEncoder(b).writeAll(chunkList).close();

            post.setEntity(new ByteArrayEntity(b.toByteArray()));
            post.setHeader(HttpHeaders.CONTENT_TYPE, Protocol.FILELIST_MIME_TYPE);

            log.info("> " + post.getRequestLine());
            try(CloseableHttpResponse response = httpClient.execute(post)) {
                log.info("< " + response.getStatusLine());

                if(Shutdown.isHappening()) {
                    return;
                }

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
                        if(Shutdown.isHappening()) {
                            post.abort();
                            return;
                        }

                        log.trace(String.format("File #%3d: %8d bytes, %s, %s", ++i, mfh.getContentLength(), mfh.getContentType(), mfh.getFilename()));
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
                            if(local.exists() && local.isDirectory()) {
                                continue;
                            }
                            log.info("mkdir     " + mfh.getFilename());
                            local.mkdirs();
                            directoriesLastModifiedTimes.add(Pair.of(local, mfh.getLastModified().getTime()));
                            continue;
                        }

                        if(local.exists()) {
                            log.info("overwrite " + mfh.getFilename());
                        } else {
                            log.info("write     " + mfh.getFilename());
                        }
                        try(FileOutputStream out = new FileOutputStream(local)) {
                            IOUtils.copy(mfh.getBody(), out);
                            totalBytes += mfh.getContentLength();
                        } catch(IOException e) {
                            log.error(String.format("Error writing to local file \"%s\": %s", fs.getLocal(), ExceptionUtils.getMessage(e)));
                            throw e;
                        }
                        local.setLastModified(mfh.getLastModified().getTime());
                    }
                }
            }
        }
    }
}
