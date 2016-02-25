/*
 * Copyright (C) 2016 B3Partners B.V.
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

import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.After;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.controller.LifecycleStage;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.opengeogroep.filesetsync.report.ClientReportPersistence;
import nl.b3p.web.stripes.ErrorMessageResolution;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/client/report/{clientId}")
public class ClientReportActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(ClientReportActionBean.class);

    private ActionBeanContext context;

    @Validate
    private String clientId;

    @Validate
    private String machineId;

    @Validate
    private String type;

    @Validate
    private String report;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    // </editor-fold>

    @After(stages = LifecycleStage.BindingAndValidation)
    public void checkMachineId() {
        if(machineId == null) {
            machineId = getContext().getRequest().getRemoteAddr();
        }
    }

    public Resolution save() {
        String ip = getContext().getRequest().getRemoteAddr();
        String msg = String.format("client %s report for client %s, machine %s, from ip: %s", type, clientId, machineId, ip);
        if(log.isTraceEnabled()) {
            log.trace("Received " + msg + ": " + report);
        }
        try {
            JSONObject reportJson = new JSONObject(report);

            ClientReportPersistence.saveClientReport(clientId, machineId, type, ip, reportJson);
            log.info("Stored " + msg);

            return new ErrorMessageResolution(HttpServletResponse.SC_OK, type + " report stored");
        } catch(Exception e) {
            log.error(String.format("Exception storing %s: %s: %s",
                    msg, e.getClass(), e.getMessage()));

            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Exception storing report: " + msg);
        }
    }
}
