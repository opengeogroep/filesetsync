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

package nl.opengeogroep.filesetsync.server;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import nl.opengeogroep.filesetsync.FileRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

/**
 *
 * @author Matthijs Laan
 */
public class ServerFileset {
    private String name;

    private String path;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Return an Iterable of FileRecords in this fileset recursing into
     * directories. The resulting FileRecords have no hash calculated.
     */
    public Iterable<FileRecord> getFileRecords() throws IOException {
        final File f = new File(path);
        if(f.isFile()) {
            return Arrays.asList(new FileRecord(f, f.getName()));
        } else {
            return new Iterable<FileRecord>() {
                @Override
                public Iterator<FileRecord> iterator() {
                    return new FileRecordIterator(f);
                }
            };
        }
    }

    public static class FileRecordIterator implements Iterator<FileRecord> {
        private final String rootPath;
        private final Iterator<File> it;

        public FileRecordIterator(File startDir) {
            this.rootPath = startDir.getAbsolutePath();
            it = FileUtils.iterateFilesAndDirs(startDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);

            // skip startDir
            it.next();
        }

        @Override
        public FileRecord next() {
            File f = it.next();
            String relativePath = f.getAbsolutePath().substring(rootPath.length()+1);
            return new FileRecord(f, relativePath);
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
