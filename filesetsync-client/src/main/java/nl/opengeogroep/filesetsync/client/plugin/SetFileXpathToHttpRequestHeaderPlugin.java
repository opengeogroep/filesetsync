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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import nl.opengeogroep.filesetsync.client.SyncJobState;
import nl.opengeogroep.filesetsync.client.config.Fileset;
import nl.opengeogroep.filesetsync.client.plugin.api.FilesetInterceptor;
import nl.opengeogroep.filesetsync.client.plugin.api.PluginContext;
import nl.opengeogroep.filesetsync.client.plugin.api.PluginInterface;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.w3c.dom.Document;

/**
 *
 * @author Matthijs Laan
 */
public class SetFileXpathToHttpRequestHeaderPlugin implements PluginInterface, FilesetInterceptor {

    private static final Log log = LogFactory.getLog(SetFileXpathToHttpRequestHeaderPlugin.class);

    PluginContext context;

    private Map<String, Properties> headers = new HashMap();

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
            String[] parts = header.split("\\.");

            if(parts.length != 2) {
                log.warn("Invalid config property: " + Arrays.toString(parts));
                continue;
            }
            header = parts[0];
            String config = parts[1];
            String value = props.getProperty(p);

            Properties headerProps = headers.get(header);
            if(headerProps == null) {
                headerProps = new Properties();
                headers.put(header, headerProps);
            }
            headerProps.put(config, value);
        }

        updateAllHeaders();

        context.addFilesetInterceptor(this);
    }

    private void updateAllHeaders() {
        for(String header: headers.keySet()) {
            updateHeader(header);
        }
    }

    private void updateHeader(String header) {
        String file = null, xpathString = null, lastModified = null, lastValue = null;
        try {
            Properties props = headers.get(header);
            if(props == null || !props.containsKey("file") || !props.containsKey("xpath")) {
                log.warn("Invalid configuration for header " + header + ", ignoring");
                return;
            }
            file = props.getProperty("file");
            xpathString = props.getProperty("xpath");
            lastModified = props.getProperty("lastModified");
            lastValue = props.getProperty("lastValue");

            File f = new File(file);
            if(!f.exists() || !f.canRead()) {
                log.warn(String.format("Cannot read value for header \"%s\" from file \"%s\"", header, file));
                return;
            }

            if(lastModified != null && lastModified.equals(f.lastModified() + "")) {
                log.trace("File for header value " + header + " was not modified, keeping value " + lastValue);
                return;
            }

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
            XPath xpath = XPathFactory.newInstance().newXPath();
            String value = xpath.evaluate(xpathString, doc);

            log.info(String.format("Value extracted from file \"%s\" using xpath \"%s\": %s",
                    file,
                    xpathString,
                    value));

            props.put("lastValue", value);
            props.put("lastModified", f.lastModified() + "");

        } catch(Exception e) {
            log.error(String.format("Error updating header %s, file=%s, xpathString=%s, lastModified=%s, lastValue=%s",
                    header, file, xpathString, lastModified, lastValue), e);
        }
    }

    @Override
    public void beforeStart(Fileset config, SyncJobState state) {

        updateAllHeaders();

        Map<String, Header> validHeaders = new HashMap();

        for(String header: headers.keySet()) {
            Properties props = headers.get(header);

            if(props.containsKey("lastValue")) {
                validHeaders.put(header, new BasicHeader(header, props.getProperty("lastValue")));
            }
        }
        List<Header> existingHeaders = state.getRequestHeaders();
        List<Header> toRemove = new ArrayList();

        for(Header existingHeader: existingHeaders) {
            if(validHeaders.containsKey(existingHeader.getName())) {
                toRemove.add(existingHeader);
            }
        }

        existingHeaders.removeAll(toRemove);
        existingHeaders.addAll(validHeaders.values());
    }

    @Override
    public void success(Fileset config, SyncJobState state) {

    }

}
