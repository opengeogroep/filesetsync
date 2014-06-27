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

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import nl.opengeogroep.filesetsync.FileRecord;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * State about synchronizing a Fileset. Includes the file list cache.
 * @author Matthijs Laan
 */
public class SyncJobState implements Serializable {
    private static final long serialVersionUID = 0L;
    
    public static final String STATE_WAITING = "waiting";
    public static final String STATE_SCHEDULED = "scheduled";
    public static final String STATE_STARTED = "started";
    public static final String STATE_RETRYING = "retrying";
    public static final String STATE_COMPLETED = "completed";
    public static final String STATE_ERROR = "error";
    
    private Date lastRun;
    
    private String currentState;
    
    private String currentAction;
    
    private Date lastFinished;
    
    private Date fileListDate;
    
    private List<FileRecord> fileList;

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

    public Date getFileListDate() {
        return fileListDate;
    }

    public void setFileListDate(Date fileListDate) {
        this.fileListDate = fileListDate;
    }

    public List<FileRecord> getFileList() {
        return fileList;
    }

    public void setFileList(List<FileRecord> fileList) {
        this.fileList = fileList;
    }
    // </editor-fold>
    
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

    void endRun(boolean success) {
        setLastFinished(new Date());
        setCurrentState(success ? STATE_COMPLETED : STATE_ERROR);

        SyncJobStatePersistence.persist();
    }
}
