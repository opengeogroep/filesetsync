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

package nl.opengeogroep.filesetsync.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 *
 * @author Matthijs Laan
 */
public class FormatUtil {

    private static final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    public static String dateToString(Date d) {
        return format.format(d);
    }

    public static long parseByteSize(String withSuffix) {
        String v = withSuffix.trim().toLowerCase();
        long factor = 1;
        String n = "";
        if(v.endsWith("kb")) {
            factor = 1024;
            n = v.substring(0, v.length() - 2).trim();
        } else if (v.endsWith("k")) {
            factor = 1024;
            n = v.substring(0, v.length() - 1).trim();
        } else if (v.endsWith("mb")) {
            factor = 1024*1024;
            n = v.substring(0, v.length() - 2).trim();
        } else if (v.endsWith("m")) {
            factor = 1024*1024;
            n = v.substring(0, v.length() - 1).trim();
        } else if (v.endsWith("gb")) {
            factor = 1024*1024*1024;
            n = v.substring(0, v.length() - 2).trim();
        } else if (v.endsWith("g")) {
            factor = 1024*1024*1024;
            n = v.substring(0, v.length() - 1).trim();
        } else if (v.endsWith("tb")) {
            factor = 1024*1024*1024*1024;
            n = v.substring(0, v.length() - 2).trim();
        } else if (v.endsWith("t")) {
            factor = 1024*1024*1024*1024;
            n = v.substring(0, v.length() - 1).trim();
        } else if (v.endsWith("b")) {
            factor = 1;
            n = v.substring(0, v.length() - 1).trim();
        }
        return Long.parseLong(n)*factor;
    }
}
