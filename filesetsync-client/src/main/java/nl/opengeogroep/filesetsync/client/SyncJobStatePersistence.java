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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import nl.opengeogroep.filesetsync.client.util.Version;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.MDC;

/**
 *
 * @author Matthijs Laan
 */
public class SyncJobStatePersistence implements Serializable {

    private static final long serialVersionUID = 0L;

    private static final Log log = LogFactory.getLog(SyncJobStatePersistence.class);

    private static SyncJobStatePersistence instance;

    private final Map<String,SyncJobState> states = new HashMap();

    public static SyncJobStatePersistence getInstance() {
        return instance;
    }

    static void initialize() {

        File f = new File(SyncConfig.getInstance().getVarDir() + File.separator + "syncjobstate.dat");
        if(!f.exists()) {
            instance = new SyncJobStatePersistence();
        } else {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(f);
                ObjectInputStream ois = new ObjectInputStream(fis);

                String version = ois.readUTF();

                if(!Version.getProjectVersion().equals(version)) {
                    log.warn("Sync state saved with version " + version + ", ignoring");
                } else {
                    instance = (SyncJobStatePersistence)ois.readObject();
                    ois.close();
                    instance.cleanup();
                    log.debug("Read sync job state from " + f.getAbsolutePath() + ", filesets: " + instance.states.keySet().toString());
                    if(log.isTraceEnabled()) {
                        for(Map.Entry<String,SyncJobState> entry: instance.states.entrySet()) {
                            log.trace("Fileset " + entry.getKey() + ": " + entry.getValue().toString());
                        }
                    }
                }
            } catch(Exception e) {
                log.error("Error reading sync job state from " + f.getAbsolutePath() + ", starting with new state", e);
                instance = new SyncJobStatePersistence();
            } finally {
                IOUtils.closeQuietly(fis);
            }
        }
    }

    public static void persist() {
        File f = new File(SyncConfig.getInstance().getVarDir() + File.separator + "syncjobstate.dat");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeUTF(Version.getProjectVersion());
            oos.writeObject(instance);
            oos.close();
        } catch(Exception e) {
            log.error("Error writing sync job state to " + f.getAbsolutePath(), e);
        } finally {
            IOUtils.closeQuietly(fos);
        }
    }

    private void cleanup() {
        Collection<String> statesToRemove = new ArrayList();
        for(String name: states.keySet()) {
            if(SyncConfig.getInstance().getFileset(name) == null) {
                statesToRemove.add(name);
            }
        }
        log.debug("Removing states for filesets not in config file: " + statesToRemove.toString());
        states.keySet().removeAll(statesToRemove);
    }

    public SyncJobState getState(String filesetName, boolean create) {
        SyncJobState state = states.get(filesetName);
        if(state == null && create) {
            state = new SyncJobState();
            states.put(filesetName, state);
        }
        return state;
    }

    public static void setCurrentFileset(Fileset fs) {
        int l = SyncConfig.getInstance().getMaxFilesetNameLength();
        if(fs == null) {
            MDC.put("fileset", StringUtils.repeat(' ', l));
        } else {
            MDC.put("fileset", StringUtils.repeat(' ', l - fs.getName().length()) + fs.getName());
        }
    }
}
