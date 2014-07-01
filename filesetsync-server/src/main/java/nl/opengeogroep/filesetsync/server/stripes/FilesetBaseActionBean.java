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
import java.util.Collection;
import java.util.Locale;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.After;
import net.sourceforge.stripes.action.Resolution;
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
import org.apache.log4j.MDC;

/**
 *
 * @author Matthijs Laan
 */
public abstract class FilesetBaseActionBean implements ActionBean, ValidationErrorHandler {
    private final Log log = LogFactory.getLog(getLogName());

    private String filesetName;

    private String subPath;

    @Validate(required=true)
    private String filesetPath;

    private ServerFileset fileset;

    protected abstract String getLogName();

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

        MDC.put("fileset", fileset.getName());
        MDC.put("subPath", getSubPath());

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

    @After(stages = LifecycleStage.RequestComplete)
    public void clearMDC() {
        MDC.clear();
    }

}
