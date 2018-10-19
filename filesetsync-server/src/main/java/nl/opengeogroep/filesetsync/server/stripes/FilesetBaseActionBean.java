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

package nl.opengeogroep.filesetsync.server.stripes;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Collection;
import java.util.Locale;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.After;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.controller.LifecycleStage;
import net.sourceforge.stripes.validation.SimpleError;
import net.sourceforge.stripes.validation.Validate;
import net.sourceforge.stripes.validation.ValidationError;
import net.sourceforge.stripes.validation.ValidationErrorHandler;
import net.sourceforge.stripes.validation.ValidationErrors;
import net.sourceforge.stripes.validation.ValidationMethod;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.filesetsync.server.ServerFileset;
import nl.opengeogroep.filesetsync.server.ServerSyncConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public abstract class FilesetBaseActionBean extends RequestLoggerActionBean implements ValidationErrorHandler {
    private final Log log = LogFactory.getLog(getLogName());

    /**
     * HTTP status code: https://tools.ietf.org/html/rfc6585#section-4
     */
    private static final int SC_TOO_MANY_REQUESTS = 429;

    private static final OperatingSystemMXBean osMXBean = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();

    private String filesetName;

    private String subPath;

    @Validate(required=true)
    private String filesetPath;

    private ServerFileset fileset;

    private String loadTooHighMessage = null;

    public String getFilesetName() {
        return filesetName;
    }

    public void setFilesetName(String filesetName) {
        this.filesetName = filesetName;
    }

    public String getSubPath() {
        return subPath;
    }

    public void setSubPath(String subPath) {
        this.subPath = subPath;
    }

    public String getFilesetPath() {
        return filesetPath;
    }

    public void setFilesetPath(String filesetPath) {
        this.filesetPath = filesetPath;
        int i = filesetPath.indexOf('/');
        if(i != -1) {
            this.filesetName = filesetPath.substring(0, i);
            this.subPath = filesetPath.substring(i);
        } else {
            this.filesetName = filesetPath;
            this.subPath = "";
        }
    }

    public ServerFileset getFileset() {
        return fileset;
    }

    public void setFileset(ServerFileset fileset) {
        this.fileset = fileset;
    }

    @After(stages = LifecycleStage.BindingAndValidation)
    public void findFileset() {
        fileset = ServerSyncConfig.getInstance().getFileset(filesetName);
    }

    public String getLocalSubPath() {
        return fileset.getPath() + subPath;
    }

    @ValidationMethod
    public void checkFileset() throws IOException {

        if(fileset == null) {
            getContext().getValidationErrors().addGlobalError(new SimpleError("Fileset path not found: " + filesetPath));
            return;
        }

        if(fileset.getMaxServerLoad() != null) {
            double sysLoad = osMXBean.getSystemLoadAverage();
            if(sysLoad > fileset.getMaxServerLoad()) {
                log.warn(String.format("%s: system load of %.2f too high (limit for fileset %.2f), returning HTTP 429 Too Many Requests, retry after %ds",
                        fileset.getName(),
                        sysLoad,
                        fileset.getMaxServerLoad(),
                        fileset.getRetryAfter()));
                loadTooHighMessage = String.format("System load of %.2f too high for fileset %s", sysLoad, fileset.getName());
                getContext().getValidationErrors().addGlobalError(new SimpleError(loadTooHighMessage));
                return;
            }
        }

        File filesetRoot = new File(fileset.getPath());
        if(!filesetRoot.exists()) {
            log.error("local path does not exist for requested fileset path " + filesetPath + ": " + fileset.getPath());
            getContext().getValidationErrors().addGlobalError(new SimpleError("Fileset does not exist on server: " + filesetPath));
            return;
        }

        // Check if subPath does not navigate with .. outside of fileset path
        // Note that .. may be stripped from HttpServletRequest.getPathInfo()
        // but Stripes also supports GET /filesetsync-server/fileset/list?filesetPath=/some/../../secret/file
        if(subPath.length() != 0) {
            if(!filesetRoot.isDirectory()) {
                getContext().getValidationErrors().addGlobalError(new SimpleError("Fileset path does not exist: " + filesetPath));
                return;
            }
            File subFile = new File(fileset.getPath() + subPath);
            subFile = subFile.getCanonicalFile();
            if(!subFile.exists()) {
                getContext().getValidationErrors().addGlobalError(new SimpleError("Fileset path does not exist: " + filesetPath));
                return;
            }

            filesetRoot = filesetRoot.getCanonicalFile();

            File parent = subFile;
            while(parent != null && !parent.equals(filesetRoot)) {
                parent = parent.getParentFile();
            }
            if(parent == null) {
                log.info(String.format("requested fileset path \"%s\" does not exist or is not subdir of fileset root \"%s\"",
                        filesetPath,
                        fileset.getPath()));
                getContext().getValidationErrors().addGlobalError(new SimpleError("Fileset path does not exist: " + filesetPath));
                return;
            }
        }
    }

    @Override
    public Resolution handleValidationErrors(ValidationErrors errors) throws Exception {

        if(loadTooHighMessage != null) {
            return new StreamingResolution("text/plain", loadTooHighMessage) {
                @Override
                protected void stream(HttpServletResponse response) throws Exception {
                    response.setStatus(SC_TOO_MANY_REQUESTS);
                    response.addIntHeader("Retry-After", fileset.getRetryAfter());
                    super.stream(response);
                }
            };
        }

        String message = "";
        for(Collection<ValidationError> errColl: errors.values()) {
            for(ValidationError err: errColl) {
                if(message.length() != 0) {
                    message += ", ";
                }
                message += err.getMessage(Locale.ENGLISH);
            }
        }
        return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, message);
    }
}
