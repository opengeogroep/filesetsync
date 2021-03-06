package nl.b3p.opengeogroep.filesetsync.report;

import java.sql.SQLException;
import javax.naming.NamingException;
import static nl.opengeogroep.filesetsync.db.DB.qr;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.json.JSONException;
import org.json.JSONObject;

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

/**
 *
 * @author Matthijs Laan
 */
public class ClientReportPersistence {
    public static final String REPORT_STARTUP = "startup";
    public static final String REPORT_STATE = "state";

    public static final void saveClientReport(String clientId, String type, String ip, JSONObject report) throws NamingException, SQLException, JSONException {

        if(REPORT_STARTUP.equals(type)) {
            long startTime = report.getLong("start_time");
            qr().update("delete from client_startup where client_id=? and start_time = to_timestamp(?)", clientId, startTime);
            qr().insert("insert into client_startup(client_id, start_time, report) values (?, to_timestamp(?), ?::json)", new ScalarHandler(), clientId, startTime, report.toString());
        } else if(REPORT_STATE.equals(type)) {
            qr().update("delete from client_state where client_id=?", clientId);
            qr().insert("insert into client_state(client_id, report_time, ip, current_state) values (?, to_timestamp(?), ?, ?::json)",
                    new ScalarHandler(),
                    clientId, System.currentTimeMillis() / 1000.0, ip, report.toString());
        } else {
            throw new IllegalArgumentException("Invalid report type: " + type);
        }
    }
}
