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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Matthijs Laan
 */
public class Version {
    private static final Properties properties = new Properties();

    static {
        InputStream gitPropsIn = null, versionPropsIn = null;
        try {
            gitPropsIn = Version.class.getResourceAsStream("/git.properties");
            versionPropsIn = Version.class.getResourceAsStream("/version.properties");
            properties.load(gitPropsIn);
            properties.load(versionPropsIn);
        } catch(IOException e) {
        } finally {
            IOUtils.closeQuietly(gitPropsIn);
            IOUtils.closeQuietly(versionPropsIn);
        }
    }
    
    public static Properties getProperties() {
        return properties;
    }
    
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}
