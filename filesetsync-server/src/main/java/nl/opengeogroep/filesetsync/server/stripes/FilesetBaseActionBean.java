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

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.After;
import net.sourceforge.stripes.controller.LifecycleStage;
import net.sourceforge.stripes.validation.SimpleError;
import net.sourceforge.stripes.validation.Validate;
import net.sourceforge.stripes.validation.ValidationMethod;
import nl.opengeogroep.filesetsync.server.ServerFileset;
import nl.opengeogroep.filesetsync.server.ServerSyncConfig;
import org.apache.log4j.MDC;

/**
 *
 * @author Matthijs Laan
 */
public abstract class FilesetBaseActionBean implements ActionBean {
    @Validate(required=true)
    private String filesetName;

    private ServerFileset fileset;

    public String getFilesetName() {
        return filesetName;
    }

    public void setFilesetName(String filesetName) {
        this.filesetName = filesetName;
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

    @ValidationMethod
    public void checkFileset() {
        if(fileset == null) {
            getContext().getValidationErrors().addGlobalError(new SimpleError("Invalid fileset name: " + filesetName));
        }
    }

    @After(stages = LifecycleStage.RequestComplete)
    public void clearMDC() {
        MDC.clear();
    }
}
