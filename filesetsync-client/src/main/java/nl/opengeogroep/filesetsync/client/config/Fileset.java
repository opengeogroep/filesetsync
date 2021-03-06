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
import java.util.Date;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import nl.opengeogroep.filesetsync.client.SyncJobState;
import nl.opengeogroep.filesetsync.client.SyncJobStatePersistence;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.json.JSONObject;

/**
 * Represents the configuration of a fileset to synchronize (either download or
 * upload).
 *
 * @author Matthijs Laan
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Fileset {
    public static final String DIRECTION_DOWNLOAD = "download";
    public static final String DIRECTION_UPLOAD = "upload";

    public static final String SCHEDULE_ONCE = "once";
    public static final String SCHEDULE_DAILY = "daily";
    public static final String SCHEDULE_HOURLY = "hourly";
    public static final String SCHEDULE_WEEKLY = "weekly";
    public static final String SCHEDULE_RETRY_AUTO = "auto";
    public static final String SCHEDULE_RETRY_QUARTER = "quarter";

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
     * When to synchronize this fileset. No schedule means run once at startup.
     */
    @XmlElement
    private String schedule = SCHEDULE_ONCE;

    /**
     * Retry schedule after job failed with error. Valid values SCHEDULE_DAILY,
     * SCHEDULE_HOURLY, SCHEDULE_WEEKLY, SCHEDULE_RETRY_AUTO. Retry schedule with
     * longer time interval than the normal schedule are interpreted as the normale
     * schedule. SCHEDULE_RETRY_AUTO means 5 minutes for hourly, 1 hour for daily
     * and weekly.
     */
    @XmlElement
    private String retrySchedule = SCHEDULE_RETRY_AUTO;

    /**
     * Priority: when another fileset with higher priority is scheduled to start
     * this fileset should suspend.
     */
    @XmlElement
    private int priority = 1;

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
     * Only transfer files matching this regular expression. Note that files
     * are still deleted for the entire path.
     */
    @XmlElement
    private String regexp;

    /**
     * Local path to save downloaded files to or upload them from. Either a
     * directory (meaning recursive) or a single file name.
     */
    @XmlElement(required=true)
    private String local;

    /**
     * Maximum number of tries after a RetryableIOException is caught.
     */
    @XmlElement
    private int maxTries = 5;

    /**
     * Alternate retry wait time in seconds.
     */
    @XmlElement
    private int retryWaitTime = 30;

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

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getRetrySchedule() {
        return retrySchedule;
    }

    public void setRetrySchedule(String retrySchedule) {
        this.retrySchedule = retrySchedule;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
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

    public String getRegexp() {
        return regexp;
    }

    public void setRegexp(String regexp) {
        this.regexp = regexp;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public int getMaxTries() {
        return maxTries;
    }

    public void setMaxTries(int maxTries) {
        this.maxTries = maxTries;
    }

    public int getRetryWaitTime() {
        return retryWaitTime;
    }

    public void setRetryWaitTime(int retryWaitTime) {
        this.retryWaitTime = retryWaitTime;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }
    // </editor-fold>

    public String getProperty(String name) {
        for(Property p: properties) {
            if(p.getName().equals(name)) {
                return p.getValue();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    public JSONObject toJSON() {
        JSONObject j = new JSONObject();
        j.put("name", name);
        j.put("schedule", schedule);
        j.put("priority", priority);
        j.put("remote", remote);
        j.put("local", local);
        j.put("regexp", regexp);
        SyncJobState state = SyncJobStatePersistence.getInstance().getState(name, false);
        if(state != null) {
            j.put("state", state.toJSON());
            Date nextScheduled = state.calculateNextRunDate(this);
            j.put("next_scheduled", nextScheduled == null ? "ASAP" : nextScheduled.getTime());
        }
        return j;
    }
}
