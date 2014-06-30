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
import java.util.HashMap;
import java.util.Map;
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

    public static void load(String configString) {
        instance = new ServerSyncConfig();

        String[] sa = configString.split(",");
        for(String s: sa) {
            String[] sp = s.split("=");
            ServerFileset sfs = new ServerFileset();
            sfs.setName(sp[0]);
            sfs.setPath(sp[1]);

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

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        load(sce.getServletContext().getInitParameter("filesets"));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
