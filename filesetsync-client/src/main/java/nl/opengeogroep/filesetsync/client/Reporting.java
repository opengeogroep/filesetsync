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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Map;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class Reporting {

    private static final Log log = LogFactory.getLog(Reporting.class);

    private static String clientId, machineId;

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

        if(machineId == null) {
            try {
                try {
                    NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                    machineId = getMacHex(ni);
                    if(machineId != null) {
                        log.debug(String.format("Using machine id from local network interface %s (%s) MAC: %s", ni.getName(), ni.getDisplayName(), machineId));
                    }
                } catch(Exception e) {
                }

                if(machineId == null) {
                    for(NetworkInterface ni: Collections.list(NetworkInterface.getNetworkInterfaces())) {

                        try {
                            if(ni.getHardwareAddress() == null || ni.isLoopback() || ni.isVirtual()) {
                                continue;
                            }

                            String name = ni.getName();
                            if(name.startsWith("vir") || name.startsWith("net") || name.startsWith("ppp")) {
                                continue;
                            }

                            String mac = getMacHex(ni);
                            if(mac.startsWith("00:00")) {
                                continue;
                            }

                            machineId = mac;
                            log.debug(String.format( "Using machine id from network interface %s (%s) MAC: %s", ni.getName(), ni.getDisplayName(), machineId));
                            break;
                        } catch(Exception e) {
                        }
                    }
                }
                if(machineId == null) {
                    log.debug("Could not determine machineId");
                    machineId = "<unknown>";
                }
            } catch(Exception e) {
                machineId = "<unknown>";
            }
        }
        log.info("Using machine id: " + machineId);
    }

    private static String getMacHex(NetworkInterface ni) throws SocketException {
        if(ni == null || ni.getHardwareAddress() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(18);
        for(byte b: ni.getHardwareAddress()) {
            if(sb.length() > 0) {
                sb.append(":");
            }
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String getClientId() {
        if(clientId == null || machineId == null) {
            initDefaultIds();
        }
        return clientId;
    }

    public static String getMachineId() {
        if(clientId == null || machineId == null) {
            initDefaultIds();
        }
        return machineId;
    }

    public static void reportClientStartup() {
        if(!SyncConfig.getInstance().isReportingEnabled()) {
            return;
        }

        getClientId();
    }
}
