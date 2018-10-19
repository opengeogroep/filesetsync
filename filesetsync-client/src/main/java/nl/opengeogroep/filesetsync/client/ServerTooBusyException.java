/*
 * Copyright (C) 2018 Expression organization is undefined on line 4, column 61 in file:///home/matthijsln/dev/dbk/filesetsync/filesetsync-client/licenseheader.txt.
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

/**
 *
 * @author matthijsln
 */
public class ServerTooBusyException extends IOException {
    public static int SC_TOO_MANY_REQUESTS = 429;

    public static int DEFAULT_RETRY_AFTER = 5 * 60;

    private final int retryAfter;

    private final String bodyMessage;

    public ServerTooBusyException(String message, String bodyMessage, Integer retryAfter) {
        super(message);
        this.bodyMessage = bodyMessage;
        this.retryAfter = retryAfter != null ? retryAfter : DEFAULT_RETRY_AFTER;
    }

    public int getRetryAfter() {
        return retryAfter;
    }

    public String getBodyMessage() {
        return bodyMessage;
    }
}
