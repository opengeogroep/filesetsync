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
package nl.opengeogroep.filesetsync.client;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.Map;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import nl.opengeogroep.filesetsync.client.util.HttpClientUtil;
import nl.opengeogroep.filesetsync.client.util.Version;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import static org.apache.http.HttpStatus.SC_OK;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
public class Reporting {
    public static final int STATE_REPORT_INTERVAL_MS = 30000;

    private static final Log log = LogFactory.getLog(Reporting.class);

    private static String clientId;

    private static boolean startupReportSent = false;
    private static JSONObject startupReport;

    public static void setClientId() {
    }

    public static void setMachineId() {
    }

    public static void initDefaultIds() {
        Map<String,String> env = System.getenv();

        if(clientId == null) {
            if(env.containsKey("COMPUTERNAME")) {
                clientId = env.get("COMPUTERNAME");
                log.info("Using COMPUTERNAME as client id: " + clientId);
            } else if(env.containsKey("HOSTNAME")) {
                clientId = env.get("HOSTNAME");
                log.info("Using HOSTNAME as client id: " + clientId);
            } else {
                try {
                    clientId = InetAddress.getLocalHost().getHostName();
                    log.info("Using local host name as client id: " + clientId);
                } catch(Exception e) {
                    clientId = "<unknown>";
                }
            }
        }
    }

    public static String getClientId() {
        if(clientId == null) {
            initDefaultIds();
        }
        return clientId;
    }

    private static boolean sendReport(final String type, final JSONObject report) {
        initDefaultIds();

        HttpUriRequest post = RequestBuilder.post()
                .setUri(SyncConfig.getInstance().getReportingURL())
                    .addParameter("type", type)
                    .addParameter("report", report.toString(4))
                    .addParameter("clientId", clientId)
                    .build();

        log.trace("> " + post.getRequestLine());

        try(CloseableHttpClient httpClient = HttpClientUtil.get()) {
            Boolean ok = httpClient.execute(post, new ResponseHandler<Boolean>() {
                @Override
                public Boolean handleResponse(HttpResponse hr) {
                    log.trace("< " + hr.getStatusLine());

                    boolean ok = hr.getStatusLine().getStatusCode() == SC_OK;
                    if(!ok) {
                        try {
                            log.error("Got HTTP error reporting " + type + " to " + SyncConfig.getInstance().getReportingURL() + ": " + IOUtils.toString(hr.getEntity().getContent(), "UTF-8"));
                        } catch(IOException e) {
                        }
                    }
                    return ok;
                }
            });

            return ok;
        } catch(Exception e) {
            log.error("Exception reporting " + type + " to " + SyncConfig.getInstance().getReportingURL() + ": " + e.getClass() +  ": " + e.getMessage());
            return false;
        }
    }

    public static void reportClientStartup() {
        if(SyncConfig.getInstance().getReportingURL() == null) {
            return;
        }

        JSONObject report = new JSONObject();
        RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
        report.put("start_time", rtBean.getStartTime() / 1000);
        report.put("config_last_modified", SyncConfig.getInstance().getConfigLastModified());

        JSONObject client = new JSONObject();
        report.put("client", client);
        client.put("name", Version.getProperty("project.name"));
        client.put("version", Version.getProperty("project.version"));
        client.put("git_commit_id", Version.getProperty("git.commit.id"));
        client.put("git_branch", Version.getProperty("git.branch"));
        client.put("git_build_time", Version.getProperty("git.build.time"));

        JSONObject os = new JSONObject();
        report.put("os", os);
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        os.put("name", osBean.getName());
        os.put("version", osBean.getVersion());
        os.put("arch", osBean.getArch());

        JSONObject runtime = new JSONObject();
        report.put("runtime", runtime);
        runtime.put("vm_name", rtBean.getVmName());
        runtime.put("vm_vendor", rtBean.getVmVendor());
        runtime.put("vm_version", rtBean.getVmVersion());
        runtime.put("name", rtBean.getName());

        startupReport = report;

        trySendingStartupReport();
    }

    public static void trySendingStartupReport() {
        if(!startupReportSent) {
            log.trace("Sending startup report " + startupReport.toString(4));
            startupReportSent = sendReport("startup", startupReport);
        }
    }

    public static void reportState() {
        reportState(false);
    }

    public static void reportState(boolean forceUpdate) {
        if(forceUpdate || AppState.getLastReported() < (System.currentTimeMillis() - STATE_REPORT_INTERVAL_MS)) {
            if(!forceUpdate) {
                trySendingStartupReport();
            }
            sendReport("state", AppState.toJSON());
            // Set last reported whether succeeded or not
            AppState.setLastReported(System.currentTimeMillis());
        }
    }
}
