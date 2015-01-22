/*
 * Copyright (C) 2015 B3Partners B.V.
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

package nl.opengeogroep.filesetsync.client.plugin.api;

import java.util.ArrayList;
import java.util.List;
import nl.opengeogroep.filesetsync.client.SyncJobState;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class PluginContext implements FilesetInterceptor {
    private static final Log log = LogFactory.getLog(PluginContext.class);

    private static PluginContext instance;

    public static void initialize() {
        PluginContext.instance = new PluginContext();
    }

    public static PluginContext getInstance() {
        return PluginContext.instance;
    }

    private final List<ApplicationInterceptor> appInterceptors = new ArrayList();
    private final List<FilesetInterceptor> filesetInterceptors = new ArrayList();

    public void addApplicationInterceptor(ApplicationInterceptor interceptor) {
        appInterceptors.add(interceptor);
    }

    public void addFilesetInterceptor(FilesetInterceptor interceptor) {
        filesetInterceptors.add(interceptor);
    }

    public List<ApplicationInterceptor> getAppInterceptors() {
        return appInterceptors;
    }

    public List<FilesetInterceptor> getFilesetInterceptors() {
        return filesetInterceptors;
    }

    @Override
    public void beforeStart(Fileset config, SyncJobState state) {
        for(FilesetInterceptor i: filesetInterceptors) {
            try {
                i.beforeStart(config, state);
            } catch(Exception e) {
                log.error("Error calling interceptor", e);
            }
        }
    }

    @Override
    public void success(Fileset config, SyncJobState state) {
        for(FilesetInterceptor i: filesetInterceptors) {
            try {
                i.success(config, state);
            } catch(Exception e) {
                log.error("Error calling interceptor", e);
            }
        }
    }
}
