package nl.opengeogroep.filesetsync.client.config;

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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Root configuration class.
 *
 * @author Matthijs Laan
 */
@XmlRootElement(name = "sync")
@XmlAccessorType(XmlAccessType.FIELD)
public class SyncConfig {
    private static final Log log = LogFactory.getLog(SyncConfig.class);

    private static SyncConfig instance;

    /**
     * List of plugins to load (in order).
     */
    @XmlElementWrapper(name = "plugins")
    @XmlElement(name = "plugin")
    List<Plugin> plugins = new ArrayList();

    /**
     * Directory to store variable information (such as fileset sync state).
     * Relative path to jar file containing main class.
     */
    @XmlElement(name = "var", required=true)
    private String varDir;

    /**
     * URL to report status to, leave empty to disable.
     */
    @XmlElement(name = "reporting")
    private String reportingURL = null;

    /**
     * Global properties.
     */
    @XmlElementWrapper(name = "globals")
    @XmlElement(name = "property")
    List<Property> properties = new ArrayList();

    /**
     * Filesets to synchronize. Executed in order.
     */
    @XmlElementWrapper(name = "filesets")
    @XmlElement(name = "fileset")
    List<Fileset> filesets = new ArrayList();

    private Long configLastModified;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    public List<Plugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public String getVarDir() {
        return varDir;
    }

    public void setVarDir(String varDir) {
        this.varDir = varDir;
    }

    public String getReportingURL() {
        return reportingURL;
    }

    public void setReportingURL(String reportingURL) {
        this.reportingURL = reportingURL;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public List<Fileset> getFilesets() {
        return filesets;
    }

    public void setFilesets(List<Fileset> filesets) {
        this.filesets = filesets;
    }

    public Long getConfigLastModified() {
        return configLastModified;
    }

    public void setConfigLastModified(Long configLastModified) {
        this.configLastModified = configLastModified;
    }
   // </editor-fold>

    public static SyncConfig getInstance() {
        return instance;
    }

    public static void load(String basePath, String filename) throws JAXBException, IOException {
        File f = new File(filename);

        InputStream in = new FileInputStream(f);
        JAXBContext jaxb = JAXBContext.newInstance(SyncConfig.class);
        Unmarshaller um = jaxb.createUnmarshaller();
        try {
            instance = (SyncConfig) um.unmarshal(in);
            instance.setConfigLastModified(f.lastModified() / 1000);
        } finally {
            in.close();
        }

        f = new File(instance.varDir);
        if(!f.isAbsolute()) {
            instance.varDir = basePath + File.separator + instance.varDir;
        }
    }

    public Fileset getFileset(String name) {
        for(Fileset fs: filesets) {
            if(fs.getName().equals(name)) {
                return fs;
            }
        }
        log.debug("Requested fileset not in configuration file by name " + name);
        return null;
    }

    public int getMaxFilesetNameLength() {
        int l = 0;
        for(Fileset fs: filesets) {
            l = Math.max(l, fs.getName().length());
        }
        return l;
    }

    public String getProperty(String name, String defaultValue) {
        for(Property p: properties) {
            if(p.getName().equals(name)) {
                return p.getValue();
            }
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
}
