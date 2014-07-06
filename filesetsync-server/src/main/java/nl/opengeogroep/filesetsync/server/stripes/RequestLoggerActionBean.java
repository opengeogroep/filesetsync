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

import javax.servlet.http.HttpServletRequest;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.After;
import net.sourceforge.stripes.controller.LifecycleStage;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.MDC;

/**
 *
 * @author Matthijs Laan <matthijslaan@b3partners.nl>
 */
public abstract class RequestLoggerActionBean implements ActionBean {
    private final Log log = LogFactory.getLog(getLogName());

    protected abstract String getLogName();

    private ActionBeanContext context;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    @After(stages=LifecycleStage.ActionBeanResolution)
    public void setMDCHeaders() {
        MDC.clear();
        HttpServletRequest r = context.getRequest();
        MDC.put("request.remoteAddr", r.getRemoteAddr());

        String s = context.getServletContext().getInitParameter("logHeaders");
        if(StringUtils.isNotBlank(s)) {
            for(String header: s.split(",")) {
                log.debug(String.format("header %s: %s", header, r.getHeader(header)));
                MDC.put("request.header." + header.toLowerCase(), r.getHeader(header));
            }
        }
        StringBuffer url = r.getRequestURL();
        if(r.getQueryString() != null) {
            url.append("?");
            url.append(r.getQueryString());
        }
        if(log.isTraceEnabled()) {
            String remoteHost = r.getRemoteHost();
            if(!remoteHost.equals(r.getRemoteAddr())) {
                log.trace("remote host: " + r.getRemoteHost());
            }
        }
    }
}
