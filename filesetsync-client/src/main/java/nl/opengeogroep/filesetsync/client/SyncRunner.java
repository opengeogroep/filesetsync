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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import static javax.swing.JOptionPane.showMessageDialog;
import static nl.opengeogroep.filesetsync.client.SyncJobState.*;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import nl.opengeogroep.filesetsync.client.util.L10n;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class SyncRunner extends Thread {

    private static final Log log = LogFactory.getLog(SyncRunner.class);

    static SyncRunner instance;

    SyncConfig config = SyncConfig.getInstance();
    SyncJobStatePersistence statePersistence = SyncJobStatePersistence.getInstance();

    public static SyncRunner getInstance() {
        if(instance == null) {
            instance = new SyncRunner();
        }
        return instance;
    }

    @Override
    public void run() {
        if(config.getFilesets().isEmpty()) {
            showMessageDialog(null, L10n.s("filesets.none"));
            return;
        }

        // Initialize all states to Waiting
        for(Fileset fs: config.getFilesets()) {
            SyncJobState state = statePersistence.getState(fs.getName(), true);
            state.setCurrentState(SyncJobState.STATE_WAITING);
        }

        doRunOnceJobs();

        if(Shutdown.isHappening()) {
            return;
        }

        boolean haveScheduled = false;
        for(Fileset fs: config.getFilesets()) {
            if(!fs.getSchedule().equals(Fileset.SCHEDULE_ONCE)) {
                haveScheduled = true;
            }
        }

        if(haveScheduled) {
            loopScheduledJobs();
        } else {
            log.info("No scheduled jobs, exiting");
        }

    }

    private void doRunOnceJobs() {
        // Iterator is in order of highest priority first

        // Do all filesets with schedule set to 'once' first, including retries
        for(Fileset fs: config.getFilesets()) {
            if(!Fileset.SCHEDULE_ONCE.equals(fs.getSchedule())) {
                continue;
            }

            SyncJobState state = SyncJobStatePersistence.getInstance().getState(fs.getName(), true);
            if(state.getCurrentState().equals(STATE_WAITING)) {
                runJobWithRetries(fs, state);
            }
        }
    }

    private void runJobWithRetries(Fileset fs, SyncJobState state) {
        new FilesetSyncer(fs).sync();

        while(state.getCurrentState().equals(STATE_RETRY)) {
            // Max wait time is 60 minutes, minimum 0
            int waitTime = Math.min(fs.getRetryWaitTime(), 60 * 1000);
            try {
                Thread.sleep(waitTime * 1000);
            } catch(InterruptedException e) {
                if(Shutdown.isHappening()) {
                    log.info("Interrupted by shutdown while waiting to retry");
                } else {
                    log.info("Interrupted while waiting to retry (no shutdown), aborting");
                }
                return;
            }
            log.info("Retrying job after waiting");
            new FilesetSyncer(fs).sync();
        }
    }

    private void runScheduledJobWithRetries(Fileset fs, SyncJobState state) {
        runJobWithRetries(fs, state);
        boolean success = state.getCurrentState().equals(STATE_COMPLETED);
        // Keep scheduled, also on error
        state.setCurrentState(STATE_WAITING);
        SyncJobStatePersistence.persist();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info(String.format("Next run date for %s %s job \"%s\": %s",
                success ? "successfully completed" : "failed",
                fs.getSchedule(),
                fs.getName(),
                sdf.format(state.calculateNextRunDate(fs))));

    }

    private void loopScheduledJobs() {

        Date earliestNextRunDate;
        Fileset earliestNextRunFileset;
        do {
            earliestNextRunDate = null;
            earliestNextRunFileset = null;

            for(Fileset fs: config.getFilesets()) {
                if(Fileset.SCHEDULE_ONCE.equals(fs.getSchedule())) {
                    continue;
                }

                SyncJobState state = SyncJobStatePersistence.getInstance().getState(fs.getName(), true);
                if(state.getCurrentState().equals(STATE_WAITING)) {
                    Date nextRunAt = state.calculateNextRunDate(fs);

                    if(nextRunAt == null || nextRunAt.before(new Date())) {
                        runScheduledJobWithRetries(fs, state);
                        nextRunAt = state.calculateNextRunDate(fs);
                    }

                    if(earliestNextRunDate == null || nextRunAt.before(earliestNextRunDate)) {
                        earliestNextRunDate = nextRunAt;
                        earliestNextRunFileset = fs;
                    }
                }
            }

            if(earliestNextRunDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                log.info(String.format("Waiting until %s to execute earliest scheduled %s job \"%s\"...",
                        sdf.format(earliestNextRunDate),
                        earliestNextRunFileset.getSchedule(),
                        earliestNextRunFileset.getName()));

                try {
                    Thread.sleep(Math.max(1000, earliestNextRunDate.getTime() - new Date().getTime() + 500));
                } catch(InterruptedException e) {
                    if(Shutdown.isHappening()) {
                        log.info("Interrupted by shutdown while waiting for next scheduled job to run");
                        return;
                    } else {
                        log.info("Interrupted while waiting for next scheduled job");
                    }
                }
            }
        } while(earliestNextRunDate != null);

        log.info("No scheduled jobs, quitting");
    }
}
