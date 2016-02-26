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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import nl.opengeogroep.filesetsync.FileRecord;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import static nl.opengeogroep.filesetsync.client.config.Fileset.*;
import nl.opengeogroep.filesetsync.protocol.Protocol;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import nl.opengeogroep.filesetsync.protocol.BufferedFileListEncoder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.Header;
import org.json.JSONObject;

/**
 * State about synchronizing a Fileset. Includes the file list cache.
 * @author Matthijs Laan
 */
public class SyncJobState implements Serializable {
    private static final long serialVersionUID = 2L;

    public static final String STATE_WAITING = "waiting";
    public static final String STATE_SCHEDULED = "scheduled";
    public static final String STATE_STARTED = "started";
    public static final String STATE_SUSPENDED = "suspended";
    public static final String STATE_RETRY = "retry";
    public static final String STATE_COMPLETED = "completed";
    public static final String STATE_ABORTED = "aborted";
    public static final String STATE_ERROR = "error";

    /** States when the job should be started immediately, instead of the
     * schedule after the last run. ERROR state is not tried again, only at the
     * next scheduled time (maybe add ERROR schedule for weekly jobs so it can
     * be tried the next day). When a job is finished with ERROR state it has
     * been retried maxTries times already.
     */
    public static final Set UNFINISHED_STATES = Collections.unmodifiableSet(new HashSet(Arrays.asList(new String[] {
        STATE_SUSPENDED,
        STATE_ABORTED
    })));

    private Date lastRun;

    private String currentState;

    private transient String currentAction;
    private transient Long currentActionSince;
    private transient Long progressCountTotal;
    private transient Long progressCount;
    private transient Long progressSizeTotal;
    private transient Long progressSize;

    private Date lastFinished;

    private transient String lastFinishedDetails;

    private Date lastSucceeded;

    private String lastFinishedState;

    private String fileListRemotePath;

    private Date fileListDate;

    private boolean fileListHashed;

    private Integer resumeFileListIndex;

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
        if(!Objects.equals(this.currentAction, currentAction)) {
            this.currentActionSince = System.currentTimeMillis();
        }
        if(currentAction == null) {
            this.currentActionSince = null;
        }
        this.currentAction = currentAction;
    }

    public Date getLastFinished() {
        return lastFinished;
    }

    public void setLastFinished(Date lastFinished) {
        this.lastFinished = lastFinished;
    }

    public String getLastFinishedState() {
        return lastFinishedState;
    }

    public void setLastFinishedState(String lastFinishedState) {
        this.lastFinishedState = lastFinishedState;
    }

    public Long getCurrentActionSince() {
        return currentActionSince;
    }

    public void setCurrentActionSince(Long currentActionSince) {
        this.currentActionSince = currentActionSince;
    }

    public Date getLastSucceeded() {
        return lastSucceeded;
    }

    public void setLastSucceeded(Date lastSucceeded) {
        this.lastSucceeded = lastSucceeded;
    }

    public String getLastFinishedDetails() {
        return lastFinishedDetails;
    }

    public void setLastFinishedDetails(String lastFinishedDetails) {
        this.lastFinishedDetails = lastFinishedDetails;
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

    public Integer getResumeFileListIndex() {
        return resumeFileListIndex;
    }

    public void setResumeFileListIndex(Integer resumeFileListIndex) {
        this.resumeFileListIndex = resumeFileListIndex;
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

    public Long getProgressCountTotal() {
        return progressCountTotal;
    }

    public void setProgressCountTotal(Long progressCountTotal) {
        this.progressCountTotal = progressCountTotal;
    }

    public Long getProgressCount() {
        return progressCount;
    }

    public void setProgressCount(Long progressCount) {
        this.progressCount = progressCount;
    }

    public Long getProgressSizeTotal() {
        return progressSizeTotal;
    }

    public void setProgressSizeTotal(Long progressSizeTotal) {
        this.progressSizeTotal = progressSizeTotal;
    }

    public Long getProgressSize() {
        return progressSize;
    }

    public void setProgressSize(Long progressSize) {
        this.progressSize = progressSize;
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

    public JSONObject toJSON() {
        JSONObject j = new JSONObject();
        j.put("state", currentState);
        j.put("last_run", lastRun == null ? null : lastRun.getTime());
        j.put("last_finished", lastFinished == null ? null : lastFinished.getTime());
        j.put("last_finished_state", lastFinishedState);
        j.put("last_finished_details", lastFinishedDetails);
        j.put("last_succeeded", lastSucceeded == null ? null : lastSucceeded.getTime());
        j.put("action", currentAction);
        j.put("action_since", currentActionSince);
        j.put("progress_count_total", progressCountTotal);
        j.put("progress_count", progressCount);
        j.put("progress_size_total", progressSizeTotal);
        j.put("progress_size", progressSize);
        return j;
    }

    void startNewRun() {
        setLastRun(new Date());
        setCurrentState(STATE_STARTED);

        // Clean future fields if necessary, leave lastFinished at old value for now

        SyncJobStatePersistence.persist();
    }

    void endRun(String state) {
        endRun(state, null);
    }

    void endRun(String state, String details) {
        setLastFinished(new Date());
        setLastFinishedState(state);
        setLastFinishedDetails(details);
        setCurrentState(state);
        setCurrentAction(null);

        if(STATE_COMPLETED.equals(state)) {
            setLastSucceeded(getLastFinished());
        }

        setProgressCountTotal(null);
        setProgressCount(null);
        setProgressSizeTotal(null);
        setProgressSize(null);

        SyncJobStatePersistence.persist();
    }

    public Date calculateNextRunDate(Fileset fs) {
        if(lastRun == null || UNFINISHED_STATES.contains(currentState)) {
            return null;
        } else {
            Calendar c = GregorianCalendar.getInstance();
            c.setTime(lastRun);

            String actualSchedule = fs.getSchedule();
            String retrySchedule = fs.getRetrySchedule();

            // Determine alternate schedule in case last run finished with error

            if(STATE_ERROR.equals(lastFinishedState) && SCHEDULE_RETRY_QUARTER.equals(retrySchedule)) {
                // Shortest interval retry setting always applies
                actualSchedule = retrySchedule;
            }

            if(STATE_ERROR.equals(lastFinishedState) && !actualSchedule.equals(retrySchedule)) {
                if(SCHEDULE_WEEKLY.equals(actualSchedule)) {
                    // For normal schedule weekly:
                    // Retry schedule setting, actual schedule on error
                    // daily                   daily
                    // hourly                  hourly
                    // auto                    hourly
                    actualSchedule = SCHEDULE_DAILY.equals(retrySchedule) ? SCHEDULE_DAILY : SCHEDULE_HOURLY;
                } else if(SCHEDULE_DAILY.equals(actualSchedule)) {
                    // For normal schedule daily:
                    // Retry schedule setting, actual schedule on error
                    // weekly                  daily
                    // hourly                  hourly
                    // auto                    hourly
                    actualSchedule = SCHEDULE_WEEKLY.equals(retrySchedule) ? SCHEDULE_DAILY : SCHEDULE_HOURLY;
                } else if(SCHEDULE_HOURLY.equals(actualSchedule)) {
                    // For normal schedule hourly:
                    // Retry schedule setting, actual schedule on error
                    // weekly                  hourly
                    // daily                   hourly
                    // auto                    quarterly
                    actualSchedule = SCHEDULE_RETRY_AUTO.equals(retrySchedule) ? SCHEDULE_RETRY_QUARTER : SCHEDULE_HOURLY;
                }
            }
            if(Fileset.SCHEDULE_HOURLY.equals(actualSchedule)) {
                c.add(Calendar.HOUR_OF_DAY, 1);
            } else if(Fileset.SCHEDULE_DAILY.equals(actualSchedule)) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            } else if(Fileset.SCHEDULE_WEEKLY.equals(actualSchedule)) {
                c.add(Calendar.WEEK_OF_YEAR, 1);
            } else if(Fileset.SCHEDULE_RETRY_QUARTER.equals(actualSchedule)) {
                c.add(Calendar.MINUTE, 15);
            } else {
                throw new IllegalArgumentException("Invalid schedule: " + fs.getSchedule());
            }
            return c.getTime();
        }
    }
}
