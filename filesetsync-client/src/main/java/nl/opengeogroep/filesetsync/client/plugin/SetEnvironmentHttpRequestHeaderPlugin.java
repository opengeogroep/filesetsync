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
package nl.opengeogroep.filesetsync.client.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import nl.opengeogroep.filesetsync.client.SyncJobState;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.client.plugin.api.FilesetInterceptor;
import nl.opengeogroep.filesetsync.client.plugin.api.PluginContext;
import nl.opengeogroep.filesetsync.client.plugin.api.PluginInterface;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

/**
 *
 * @author Matthijs Laan
 */
public class SetEnvironmentHttpRequestHeaderPlugin implements PluginInterface, FilesetInterceptor  {

    private static final Log log = LogFactory.getLog(SetEnvironmentHttpRequestHeaderPlugin.class);

    PluginContext context;

    private final Map<String,String> headers = new HashMap();

    @Override
    public void setPluginContext(PluginContext context) {
        this.context = context;
    }

    @Override
    public void configure(Properties props) {

        for(String p: props.stringPropertyNames()) {

            if(!p.startsWith("header.")) {
                log.warn("Ignoring config property: " + p);
                continue;
            }

            String header = p.substring("header".length()+1);
            String value = props.getProperty(p);

            value = System.getenv(value);
            if(value != null) {
                headers.put(header, value);
            }
        }

        context.addFilesetInterceptor(this);
    }

    @Override
    public void beforeStart(Fileset config, SyncJobState state) {
        List<Header> existingHeaders = state.getRequestHeaders();

        for(Map.Entry<String,String> h: headers.entrySet()) {
            boolean exists = false;
            for(Header existingHeader: existingHeaders) {
                if(existingHeader.getName().equals(h.getKey())) {
                    exists = true;
                    break;
                }
            }
            if(!exists) {
                existingHeaders.add(new BasicHeader(h.getKey(), h.getValue()));
            }
        }
    }

    @Override
    public void success(Fileset config, SyncJobState state) {
    }
}
