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

package nl.opengeogroep.filesetsync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Iterator;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.CanReadFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 *
 * @author Matthijs Laan
 */
public class FileRecord implements Serializable {
    private static final long serialVersionUID = 0L;

    public static final char TYPE_DIRECTORY = 'd';
    public static final char TYPE_FILE = 'f';
    public static final char TYPE_OTHER = 'o';

    private char type;

    /**
     * Path relative to the fileset root.
     */
    private String name;

    private long size;

    private long lastModified;

    private String hash;

    private transient File file;

    public FileRecord() {
    }

    public FileRecord(File f, String name) {
        this.file = f;
        if(f.isFile()) {
            type = TYPE_FILE;
            this.size = f.length();
        } else if(f.isDirectory()) {
            type = TYPE_DIRECTORY;
            this.size = 0;
        } else {
            // special or deleted file
            type = TYPE_OTHER;
        }

        this.name = name;
        this.lastModified = f.lastModified();
    }

    public static char typeOf(File file) {
        if(file.isFile()) {
            return TYPE_FILE;
        } else if(file.isDirectory()) {
            return TYPE_DIRECTORY;
        } else {
            return TYPE_OTHER;
        }
    }

    /**
     * Return an Iterable of FileRecords in this fileset recursing into
     * directories. The resulting FileRecords have no hash calculated.
     */
    public static Iterable<FileRecord> getFileRecordsInDir(String path) throws IOException {
        final File f = new File(path);
        if(f.isFile()) {
            return Arrays.asList(new FileRecord(f, "."));
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
            it = FileUtils.iterateFilesAndDirs(startDir, new AndFileFilter(FileFileFilter.FILE, CanReadFileFilter.CAN_READ), TrueFileFilter.INSTANCE);
        }

        @Override
        public FileRecord next() {
            File f = it.next();
            String absolutePath = f.getAbsolutePath();
            if(absolutePath.equals(rootPath)) {
                // Root directory, set "." as relative name
                return new FileRecord(f, ".");
            } else {
                return new FileRecord(f, absolutePath.substring(rootPath.length()+1));
            }
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

    public void calculateHash() throws FileNotFoundException, IOException {
        calculateHash(null);
    }

    public void calculateHash(MutableLong hashTimeMillisAccumulator) throws FileNotFoundException, IOException {
        this.hash = calculateHash(file, hashTimeMillisAccumulator);
    }

    public static String calculateHash(File f, MutableLong hashTimeMillisAccumulator) throws FileNotFoundException, IOException {

        long startTime = hashTimeMillisAccumulator == null ? 0 : System.currentTimeMillis();

        // On Windows do not use memory mapped files, because the client may
        // want to overwrite a file it has just calculated the checksum of.
        // http://bugs.java.com/view_bug.do?bug_id=4724038

        // Performance difference is minimal or negative in some tests

        //String hash = SystemUtils.IS_OS_WINDOWS ? calculateHashNormalIO(f) : calculateHashMappedIO(f);
        String hash = calculateHashNormalIO(f);

        if(hashTimeMillisAccumulator != null) {
            hashTimeMillisAccumulator.add(System.currentTimeMillis() - startTime);
        }
        return hash;
    }

    public static String calculateHashNormalIO(File f) throws FileNotFoundException, IOException {
        try (
            InputStream in = new FileInputStream(f);
        ) {
            return DigestUtils.md5Hex(in);
        }
    }

    public static String calculateHashMappedIO(File f) throws FileNotFoundException, IOException {
        try (
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            FileChannel channel = raf.getChannel();
        ) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.load();

            MessageDigest md = DigestUtils.getMd5Digest();
            md.update(buffer);
            byte[] digest = md.digest();

            return Hex.encodeHexString(digest);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    public char getType() {
        return type;
    }

    public void setType(char type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
    // </editor-fold>

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SIMPLE_STYLE);
    }
}
