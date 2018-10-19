/*
 * Copyright (C) 2018 B3Partners B.V.
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
package nl.opengeogroep.filesetsync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import static nl.opengeogroep.filesetsync.FileRecord.TYPE_DIRECTORY;
import static nl.opengeogroep.filesetsync.FileRecord.TYPE_FILE;
import nl.opengeogroep.filesetsync.protocol.BufferedFileListEncoder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

/**
 *
 * @author matthijsln
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if(args.length != 2) {
            System.err.println("Arguments: <file list> <dir to list>");
            System.exit(1);
        }
        long startTime = System.currentTimeMillis();
        long lastModified = 0;
        String lastModifiedFile = null;
        try(OutputStream out = new FileOutputStream(args[0])) {
            final BufferedFileListEncoder encoder = new BufferedFileListEncoder(out);

            Iterable<FileRecord> records = FileRecord.getFileRecordsInDir(args[1], null, null);
            int files = 0, dirs = 0;
            long totalBytes = 0;
            try {
                for(FileRecord fr: records) {
                    if(fr.getLastModified() > lastModified) {
                        lastModified = fr.getLastModified();
                        lastModifiedFile = fr.getName();
                    }
                    switch (fr.getType()) {
                        case TYPE_FILE:
                            files++;
                            totalBytes += fr.getSize();
                            encoder.write(fr);
                            break;
                        case TYPE_DIRECTORY:
                            dirs++;
                            encoder.write(fr);
                            break;
                        default:
                            System.err.println("not a file or directory (special or deleted file), ignoring " + fr.getFile());
                            break;
                    }

                }
            } catch(IOException e) {
                e.printStackTrace();
            } finally {
                IOUtils.closeQuietly(encoder);
                IOUtils.closeQuietly(out);

                System.err.printf("listed %d directories and %d files, total size %d KB, time %s\n",
                        dirs,
                        files,
                        totalBytes / 1024,
                        DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, false)
                );
            }
        }
        if(lastModified != 0) {
            new File(args[0]).setLastModified(lastModified);
            System.err.printf("set last modified of listing to file %s of %tc\n", lastModifiedFile, lastModified);
        }
    }
}
