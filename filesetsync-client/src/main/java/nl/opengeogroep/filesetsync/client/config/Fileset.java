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

package nl.opengeogroep.filesetsync.client.config;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Represents the configuration of a fileset to synchronize (either download or
 * upload).
 *
 * @author Matthijs Laan
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Fileset implements Comparable {
    public static final String DIRECTION_DOWNLOAD = "download";
    public static final String DIRECTION_UPLOAD = "upload";

    public static final String SCHEDULE_ONCE = "once";
    public static final String SCHEDULE_DAILY = "daily";
    public static final String SCHEDULE_HOURLY = "hourly";

    /**
     * Name for identification.
     */
    @XmlAttribute(required=true)
    private String name;

    /**
     * URL to filesetsync-server web application.
     */
    @XmlAttribute(required=true)
    private String server;

    /**
     * Relative priority of this fileset to other filesets. Lower numbers will
     * be prioritized to sync first.
     */
    @XmlElement
    private Double priority;

    /**
     * When to synchronize this fileset. No schedule means run once at startup.
     */
    @XmlElement(defaultValue=SCHEDULE_ONCE)
    private String schedule;

    /**
     * One of &quot;download&quot; or &quot;upload&quot;.
     */
    @XmlElement(required=true)
    private String direction;

    /**
     * If true, compare files by hash instead of last modified date.
     */
    @XmlElement
    private boolean hash = false;

    /**
     * Delete local files not existing in server file list, default false. When
     * uploading the server may decide to ignore deletes.
     */
    @XmlElement
    private boolean delete = false;

    /**
     * Remote name to download files from or upload them to. Either a directory
     * name (meaning recursive) or a single file name.
     */
    @XmlElement(required=true)
    private String remote;

    /**
     * Local path to save downloaded files to or upload them from. Either a
     * directory (meaning recursive) or a single file name.
     */
    @XmlElement(required=true)
    private String local;

    /**
     * Properties for this fileset.
     */
    @XmlElementWrapper(name="properties")
    @XmlElement(name="property")
    private List<Property> properties = new ArrayList();

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public Double getPriority() {
        return priority;
    }

    public void setPriority(Double priority) {
        this.priority = priority;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean isHash() {
        return hash;
    }

    public void setHash(boolean hash) {
        this.hash = hash;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public String getRemote() {
        return remote;
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }
    // </editor-fold>

    public int compareTo(Object other) {
        Fileset rhs = (Fileset)other;

        if(rhs.priority == null) {
            // Higher priority
            return -1;
        } else if(priority == null) {
            // Lower priority
            return 1;
        } else {
            return priority.compareTo(rhs.priority);
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
}
