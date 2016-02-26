/*
 * Copyright (C) 2016 B3Partners B.V.
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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
public class AppState {
    private static final Log log = LogFactory.getLog(AppState.class);

    private static final String CHARSET = "UTF-8";

    private static String mode = "starting";
    private static long modeSince = System.currentTimeMillis();
    private static Fileset currentFileset;
    private static long lastReported;

    private static final Map<String,Object> stats = new HashMap();

    private static File getFile() {
        return new File(SyncConfig.getInstance().getVarDir() + File.separator + "stats.json");
    }

    public static void load() {

        File f = getFile();
        if(f.exists()) {
            try {
                JSONObject j = new JSONObject(IOUtils.toString(new FileInputStream(f), CHARSET));

                for(Object k: j.keySet()) {
                    stats.put((String)k, j.get((String)k));
                }
            } catch(Exception e) {
                log.error("Failed to load stats, stats reset! " + e.getClass() + ": " + e.getMessage());
            }
        }
    }

    public static void save() {
        try {
            JSONObject j = new JSONObject();
            for(Map.Entry<String,Object> e: stats.entrySet()) {
                j.put(e.getKey(), e.getValue());
            }
            FileUtils.writeStringToFile(getFile(), j.toString(4), CHARSET);
        } catch(IOException e) {
            log.error("Error saving stats: " + e.getClass() + ": " + e.getMessage());
        }
    }

    public static long getLastReported() {
        return lastReported;
    }

    public static void setLastReported(long lastReported) {
        AppState.lastReported = lastReported;
    }

    public static Map<String, Object> getStats() {
        return stats;
    }

    public static void addStatsValue(String stat, int value) {
        Object v = stats.get(stat);
        if(v == null || !(v instanceof Integer)) {
            stats.put(stat, value);
        } else {
            stats.put(stat, (Integer)v + value);
        }
    }

    public static void updateMode(String mode) {
        AppState.mode = mode;
        AppState.modeSince = System.currentTimeMillis();
    }

    public static void updateCurrentFileset(Fileset fs) {
        AppState.currentFileset = fs;
    }

    public static JSONObject toJSON() {
        JSONObject j = new JSONObject();
        j.put("mode", mode);
        j.put("mode_since", modeSince);
        if(currentFileset != null) {
            j.put("current_fileset", currentFileset.getName());
        }

        JSONArray filesets = new JSONArray();
        j.put("filesets", filesets);
        for(Fileset fs: SyncConfig.getInstance().getFilesets()) {
            filesets.put(fs.toJSON());
        }

        JSONObject js = new JSONObject();
        j.put("stats", js);
        for(Map.Entry<String,Object> e: stats.entrySet()) {
            js.put(e.getKey(), e.getValue());
        }

        return j;
    }
}
