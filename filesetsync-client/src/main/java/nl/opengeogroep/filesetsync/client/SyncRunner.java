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

import nl.opengeogroep.filesetsync.client.util.L10n;
import static javax.swing.JOptionPane.showMessageDialog;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class SyncRunner extends Thread {
    
    private static final Log log = LogFactory.getLog(SyncRunner.class);
    
    static SyncRunner instance;
    
    public static SyncRunner getInstance() {
        if(instance == null) {
            instance = new SyncRunner();
        }
        return instance;
    }
    
    @Override
    public void run() {
        SyncConfig config = SyncConfig.getInstance();
        
        // Never give up
        while(true) {
            /* check all filesets and check in priority order if it should run
             * according to schedule.
             */

            /* We do not use Quartz as it can only persistently store jobs in a 
             * database. Using an embedded JavaDB adds stupid amounts of complexity.
             * Downside is we only support a "daily" or "hourly" schedule at the
             * moment.
             */

            log.info("Checking filesets to synchronize...");
            
            if(config.getFilesets().isEmpty()) {
                showMessageDialog(null, L10n.s("filesets.none"));
                return;
            }
            
            SyncJobStatePersistence statePersistence = SyncJobStatePersistence.getInstance();
            
            // Initialize all states to Waiting
            for(Fileset fs: config.getFilesets()) {
                SyncJobState state = statePersistence.getState(fs.getName(), true);
                state.setCurrentState(SyncJobState.STATE_WAITING);
            }
            
            // Iterator is in order of highest priority first
            for(Fileset fs: config.getFilesets()) {
                
                String schedule = fs.getSchedule();
                
                if(Fileset.SCHEDULE_ONCE.equals(schedule)) {
                    new FilesetSyncer(fs).sync();
                } else { 
                    throw new UnsupportedOperationException();
                }
            }
        }   
    }
}
