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
            if(!UNFINISHED_STATES.contains(state.getCurrentState())) {
                state.setCurrentState(SyncJobState.STATE_WAITING);
            }
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

        // TODO: in priority order

        // Do all filesets with schedule set to 'once' first, including retries
        for(Fileset fs: config.getFilesets()) {
            if(!Fileset.SCHEDULE_ONCE.equals(fs.getSchedule())) {
                continue;
            }

            SyncJobState state = SyncJobStatePersistence.getInstance().getState(fs.getName(), true);
            if(state.getCurrentState().equals(STATE_WAITING)) {
                runJobWithRetries(fs, null, state);
            }
        }
    }

    private void runJobWithRetries(Fileset fs, Date endTime, SyncJobState state) {
        new FilesetSyncer(fs, endTime).sync();

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
            new FilesetSyncer(fs, endTime).sync();
        }
    }

    private void runScheduledJobWithRetries(Fileset fs, Date endTime, SyncJobState state) {
        runJobWithRetries(fs, endTime, state);

        boolean success = state.getCurrentState().equals(STATE_COMPLETED);
        if(STATE_SUSPENDED.equals(state.getCurrentState())) {
            SyncJobStatePersistence.persist();
            log.info(String.format("Job \"%s\" suspended", fs.getName()));
        } else {
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
    }

    private static class ScheduleInfo {
        Fileset filesetToRun;
        SyncJobState filesetToRunState;
        Date startTime;
        Date higherPriorityStartTime;
        Fileset nextHigherPriorityFileset;
    }

    /**
     * Find the with the earliest start date and the date when a higher priority
     * fileset is scheduled, or if no filesets are to be started, the date for
     * the earliest next run.
     */
    private ScheduleInfo getScheduleInfo() {
        ScheduleInfo info = new ScheduleInfo();
        Integer highestPriority = null;

        for(Fileset fs: config.getFilesets()) {
            if(Fileset.SCHEDULE_ONCE.equals(fs.getSchedule())) {
                continue;
            }
            SyncJobState state = SyncJobStatePersistence.getInstance().getState(fs.getName(), true);
            if(!(STATE_WAITING.equals(state.getCurrentState()) || STATE_SUSPENDED.equals(state.getCurrentState()))) {
                continue;
            }

            log.debug("Determinig schedule for job " + fs.getName() + ", state: " + state.toString());

            // First fileset we're looking at, or higher priority than currently
            // selected fileset?
            if(highestPriority == null || fs.getPriority() > highestPriority) {
                highestPriority = fs.getPriority();
                info.filesetToRun = fs;
                info.filesetToRunState = state;
                info.startTime = state.calculateNextRunDate(fs);
            } else if(fs.getPriority() == highestPriority) {
                // next run date = null -> start immediately, same priority in
                // filesets config order
                if(info.startTime != null) {
                    Date fsNextRun = state.calculateNextRunDate(fs);
                    if(fsNextRun == null || fsNextRun.before(info.startTime)) {
                        info.filesetToRun = fs;
                        info.filesetToRunState = state;
                        info.startTime = fsNextRun;
                    }
                }
            } else if(info.startTime != null && info.startTime.getTime() > System.currentTimeMillis()) {
                // Lower priority can run if current job is not immediately
                // scheduled and lower priority is immediately scheduled or
                // before higher priority job
                Date fsNextRun = state.calculateNextRunDate(fs);
                if(fsNextRun == null || fsNextRun.before(info.startTime)) {
                    info.filesetToRun = fs;
                    info.filesetToRunState = state;
                    info.startTime = fsNextRun;
                }
            }
        }

        if(info.filesetToRun == null) {
            throw new IllegalStateException("No scheduled jobs, should have exited!");
        }

        // Find job with higher priority than fileset we are running, to determine
        // end time for fileset to run
        for(Fileset fs: config.getFilesets()) {
            if(Fileset.SCHEDULE_ONCE.equals(fs.getSchedule())) {
                continue;
            }
            SyncJobState state = SyncJobStatePersistence.getInstance().getState(fs.getName(), true);
            if(!STATE_WAITING.equals(state.getCurrentState())) {
                continue;
            }

            if(fs == info.filesetToRun) {
                continue;
            }

            if(fs.getPriority() > info.filesetToRun.getPriority()) {
                // Can only get here if calculated next run time is after start
                // time
                Date nextRunTime = state.calculateNextRunDate(fs);
                if(info.higherPriorityStartTime == null || info.higherPriorityStartTime.after(nextRunTime)) {
                    info.higherPriorityStartTime = nextRunTime;
                    info.nextHigherPriorityFileset = fs;
                }
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info(String.format("Schedule: next job to run is \"%s\" (prio %d%s)%s",
                info.filesetToRun.getName(),
                info.filesetToRun.getPriority(),
                STATE_SUSPENDED.equals(info.filesetToRunState.getCurrentState()) ? ", was suspended" : "",
                info.startTime == null ? ", immediately" : ", at " + sdf.format(info.startTime)));
        if(info.nextHigherPriorityFileset != null) {
            log.info(String.format("Until higher priority job \"%s\" (prio %d) is scheduled to start at %s",
                    info.nextHigherPriorityFileset.getName(),
                    info.nextHigherPriorityFileset.getPriority(),
                    sdf.format(info.higherPriorityStartTime)));
        }

        return info;
    }

    private void loopScheduledJobs() {
        while(true) {
            ScheduleInfo schedule = getScheduleInfo();

            if(schedule.startTime != null) {
                try {
                    Thread.sleep(Math.max(1000, schedule.startTime.getTime() - new Date().getTime() + 500));
                } catch(InterruptedException e) {
                    if(Shutdown.isHappening()) {
                        log.info("Interrupted by shutdown while waiting for next scheduled job to run");
                        return;
                    } else {
                        log.info("Interrupted while waiting for next scheduled job");
                    }
                }
            }

            runScheduledJobWithRetries(schedule.filesetToRun, schedule.higherPriorityStartTime, SyncJobStatePersistence.getInstance().getState(schedule.filesetToRun.getName(), true));

            if(Shutdown.isHappening()) {
                return;
            }
        }
    }
}
