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

import static nl.opengeogroep.filesetsync.client.util.FormatUtil.*;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class FilesetSyncer {
    private static final Log log = LogFactory.getLog(FilesetSyncer.class);
    
    private SyncJobState state;
    
    private Fileset fs;
    
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
        
        retrieveFilesetList();
        compareFilesetList();
        transferFiles();
        
        state.setCurrentState("Retrieving file list");

        
        log.info("Sync job complete");
        state.endRun(true);
    }
    
    private void retrieveFilesetList() {
    }
    
    private void compareFilesetList() {
    }
    
    private void transferFiles() {
    }
}
