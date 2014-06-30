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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 *
 * @author Matthijs Laan
 */
public class FileRecord {
    private static final long serialVersionUID = 0L;

    private static final char TYPE_DIRECTORY = 'd';
    private static final char TYPE_FILE = 'f';

    private char type;
    /**
     * Path relative to the fileset root.
     */
    private String name;

    private long size;

    private long lastModified;

    private String hash;

    public FileRecord() {
    }

    public FileRecord(File f, String name) {
        if(f.isFile()) {
            type = TYPE_FILE;
            this.size = f.length();
        } else if(f.isDirectory()) {
            type = TYPE_DIRECTORY;
            this.size = 0;
        } else {
            throw new IllegalArgumentException("File " + f + " is not a directory or file");
        }

        this.name = name;
        this.lastModified = f.lastModified();
    }

    public void calculateHash(File f) throws FileNotFoundException, IOException {

        // On Windows do not use memory mapped files, because the client may
        // want to overwrite a file it has just calculated the checksum of.
        // http://bugs.java.com/view_bug.do?bug_id=4724038

        // For the server using memory mapped files should be faster as no
        // copying should be required between the OS file cache and the JVM and
        // multiple threads could use the same mapped memory.

        if(SystemUtils.IS_OS_WINDOWS) {
            calculateHashNormalIO(f);
        } else {
            calculateChecksumMappedIO(f);
        }
    }

    public void calculateHashNormalIO(File f) throws FileNotFoundException, IOException {
        try (
            InputStream in = new FileInputStream(f);
        ) {
            hash = DigestUtils.sha1Hex(in);
        }
    }

    public void calculateChecksumMappedIO(File f) throws FileNotFoundException, IOException {
        try (
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            FileChannel channel = raf.getChannel();
        ) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.load();

            MessageDigest md = DigestUtils.getSha1Digest();
            md.update(buffer);
            byte[] digest = md.digest();

            hash = Hex.encodeHexString(digest);
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
    // </editor-fold>

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SIMPLE_STYLE);
    }
}
