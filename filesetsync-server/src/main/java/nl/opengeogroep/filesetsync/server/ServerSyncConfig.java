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

package nl.opengeogroep.filesetsync.server;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class ServerSyncConfig implements ServletContextListener {
    private static final Log log = LogFactory.getLog(ServerSyncConfig.class);

    private static ServerSyncConfig instance;

    private final Map<String,ServerFileset> filesets = new HashMap();

    public static ServerSyncConfig getInstance() {
        return instance;
    }

    public static void load(String configString, String listingsConfig, String maxLoadConfig) {
        instance = new ServerSyncConfig();

        Map<String,String> listings = new HashMap();
        for(String s: listingsConfig.split(",")) {
            String[] sp = s.split("=");
            listings.put(sp[0], sp[1]);
        }

        Map<String,Double> maxLoads = new HashMap();
        for(String s: maxLoadConfig.split(",")) {
            String[] sp = s.split("=");
            maxLoads.put(sp[0], Double.parseDouble(sp[1]));
        }

        String[] sa = configString.split(",");
        for(String s: sa) {
            String[] sp = s.split("=");
            ServerFileset sfs = new ServerFileset();
            sfs.setName(sp[0]);
            sfs.setPath(sp[1]);
            sfs.setListing(listings.get(sfs.getName()));
            sfs.setMaxServerLoad(maxLoads.get(sfs.getName()));

            File f = new File(sfs.getPath());

            if(!f.exists() || !f.canRead()) {
                log.error("Fileset " + f.getName() + " non-existing or unreadable path: " + sfs.getPath());
            }

            instance.filesets.put(sfs.getName(), sfs);
        }
    }

    public ServerFileset getFileset(String name) {
        return filesets.get(name);
    }

    public Set<String> getFilesetNames() {
        return Collections.unmodifiableSet(filesets.keySet());
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        load(sce.getServletContext().getInitParameter("filesets"),
                sce.getServletContext().getInitParameter("filesets_listings"),
                sce.getServletContext().getInitParameter("filesets_maxserverload")
        );
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
