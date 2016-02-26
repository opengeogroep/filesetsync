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

package nl.opengeogroep.filesetsync.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan <matthijslaan@b3partners.nl>
 */
public class Shutdown extends Thread {
    private static final Log log = LogFactory.getLog(Shutdown.class);

    private static boolean happening = false;

    public static boolean isHappening() {
        return happening;
    }

    @Override
    public void run() {
        log.info("Shutdown hook called");
        happening = true;
        SyncRunner.getInstance().interrupt();
        AppState.save();
    }
}
