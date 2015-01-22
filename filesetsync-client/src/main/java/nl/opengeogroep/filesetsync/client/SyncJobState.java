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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import nl.opengeogroep.filesetsync.FileRecord;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.protocol.Protocol;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import nl.opengeogroep.filesetsync.protocol.BufferedFileListEncoder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.Header;

/**
 * State about synchronizing a Fileset. Includes the file list cache.
 * @author Matthijs Laan
 */
public class SyncJobState implements Serializable {
    private static final long serialVersionUID = 0L;

    public static final String STATE_WAITING = "waiting";
    public static final String STATE_SCHEDULED = "scheduled";
    public static final String STATE_STARTED = "started";
    public static final String STATE_RETRY = "retry";
    public static final String STATE_COMPLETED = "completed";
    public static final String STATE_ERROR = "error";

    private Date lastRun;

    private String currentState;

    private String currentAction;

    private Date lastFinished;

    private String fileListRemotePath;

    private Date fileListDate;

    private boolean fileListHashed;

    private transient int failedTries;

    private transient List<Header> requestHeaders;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    public Date getLastRun() {
        return lastRun;
    }

    public void setLastRun(Date lastRun) {
        this.lastRun = lastRun;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public String getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(String currentAction) {
        this.currentAction = currentAction;
    }

    public Date getLastFinished() {
        return lastFinished;
    }

    public void setLastFinished(Date lastFinished) {
        this.lastFinished = lastFinished;
    }

    public String getFileListRemotePath() {
        return fileListRemotePath;
    }

    public void setFileListRemotePath(String fileListRemotePath) {
        this.fileListRemotePath = fileListRemotePath;
    }

    public Date getFileListDate() {
        return fileListDate;
    }

    public void setFileListDate(Date fileListDate) {
        this.fileListDate = fileListDate;
    }

    public boolean isFileListHashed() {
        return fileListHashed;
    }

    public void setFileListHashed(boolean fileListHashed) {
        this.fileListHashed = fileListHashed;
    }

    public int getFailedTries() {
        return failedTries;
    }

    public void setFailedTries(int failedTries) {
        this.failedTries = failedTries;
    }

    public List<Header> getRequestHeaders() {
        if(requestHeaders == null) {
            requestHeaders = new ArrayList();
        }
        return requestHeaders;
    }

    public void setRequestHeaders(List<Header> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }
    // </editor-fold>

    public static void writeCachedFileList(String name, List<FileRecord> fileList) throws IOException {
        try(
                GZIPOutputStream gzOut = new GZIPOutputStream(new FileOutputStream(getFileListCacheFile(name)));
                BufferedFileListEncoder encoder = new BufferedFileListEncoder(gzOut)) {
            for (FileRecord fr: fileList) {
                encoder.write(fr);
            }
        }
    }

    private static File getFileListCacheFile(String name) {
        return new File(SyncConfig.getInstance().getVarDir() + File.separator + name + ".filelist.txt.gz");
    }

    public static boolean haveCachedFileList(String name) {
        File f = getFileListCacheFile(name);
        return f.exists() && f.canRead();
    }

    public static List<FileRecord> readCachedFileList(String name) throws IOException {
        try(GZIPInputStream gzIn = new GZIPInputStream(new FileInputStream(getFileListCacheFile(name)))) {
            return Protocol.decodeFilelist(gzIn);
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);

    }

    void startNewRun() {
        setLastRun(new Date());
        setCurrentState(STATE_STARTED);

        // Clean future fields if necessary, leave lastFinished at old value for now

        SyncJobStatePersistence.persist();
    }

    void endRun(String state) {
        setLastFinished(new Date());
        setCurrentState(state);

        SyncJobStatePersistence.persist();
    }

    public Date calculateNextRunDate(Fileset fs) {
        if(lastRun == null) {
            return null;
        } else {
            Calendar c = GregorianCalendar.getInstance();
            c.setTime(lastRun);
            if(Fileset.SCHEDULE_HOURLY.equals(fs.getSchedule())) {
                c.add(Calendar.HOUR_OF_DAY, 1);
            } else {
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            return c.getTime();
        }
    }
}
