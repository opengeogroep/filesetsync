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

package nl.opengeogroep.filesetsync.client.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpDateGenerator;

/**
 *
 * @author Matthijs Laan
 */
public class HttpClientUtil {
    private static final DateFormat format = new SimpleDateFormat(HttpDateGenerator.PATTERN_RFC1123, Locale.US);
    
    static {
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    public static CloseableHttpClient get() {
        return HttpClients.custom()
                .setUserAgent(Version.getProperty("project.name") + "/" + Version.getProperty("project.version"))
                .build();
    }
    
    public static String format(Date date) {
        return format.format(date);
    }
}
