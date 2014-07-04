
package nl.opengeogroep.filesetsync;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import nl.opengeogroep.filesetsync.util.HttpUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;

/**
 *
 * @author Matthijs Laan
 */
public class Protocol {

    public static final String FILELIST_ENCODING = "UTF-8";
    public static final String FILELIST_MIME_TYPE = "application/x-filesetsync-filelist";
    private static final String FILELIST_HEADER_START = "filesetsync:filelist:start";
    private static final String FILELIST_HEADER_END = "filesetsync:filelist:end";

    public static final String MULTIFILE_MIME_TYPE = "application/x-filesetsync-multifile";
    private static final String CONTENT_TYPE_DIRECTORY = "application/x-filesetsync-directory";
    private static final String HEADER_FILENAME = "X-Filesetsync-Filename";

    public static List<FileRecord> decodeFilelist(InputStream in) throws IOException {
        // Poorly-encoded text decoding

        List<FileRecord> l = new ArrayList();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, FILELIST_ENCODING));
        String line = br.readLine();
        if(line == null || !FILELIST_HEADER_START.equals(line)) {
            throw new IOException("Wrong filelist format returned, bad server URL? Output: " + line);
        }
        while((line = br.readLine()) != null) {
            if(FILELIST_HEADER_END.equals(line)) {
                return l;
            }
            String[] s = line.split("\\|");
            FileRecord r = new FileRecord();
            r.setType(s[0].charAt(0));
            r.setName(s[1]);
            r.setSize(Long.parseLong(s[2]));
            r.setLastModified(Long.parseLong(s[3]));
            r.setHash("null".equals(s[4]) ? null : s[4]);
            l.add(r);
        }

        throw new EOFException(String.format("fileset list not properly ended after reading %d file records, server problem?", l.size()));
    }

    public static class BufferedFileRecordEncoder implements AutoCloseable {
        private BufferedWriter writer = null;

        private boolean headerWritten = false;

        public BufferedFileRecordEncoder(OutputStream out) {
            try {
                this.writer = new BufferedWriter(new OutputStreamWriter(out, FILELIST_ENCODING));
            } catch (UnsupportedEncodingException ex) {
            }
        }

        public void write(FileRecord f) throws IOException {
            if(!headerWritten) {
                writer.append(FILELIST_HEADER_START + "\n");
                headerWritten = true;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(f.getType());
            sb.append("|");
            sb.append(f.getName());
            sb.append("|");
            sb.append(f.getSize());
            sb.append("|");
            sb.append(f.getLastModified());
            sb.append("|");
            sb.append(f.getHash() != null ? f.getHash() : "null");
            sb.append("\n");
            writer.append(sb);
        }

        @Override
        public void close() throws IOException {
            writer.append(FILELIST_HEADER_END + "\n");
            writer.close();
        }
    }

    public static class MultiFileEncoder implements AutoCloseable {
        final private DataOutputStream out;

        final Log log;

        public MultiFileEncoder(OutputStream out, Log log) {
            this.out = new DataOutputStream(out);
            this.log = log;
        }

        private class MultiFileResponseHeaderWriter {
            public MultiFileResponseHeaderWriter(int status, String statusLine, String filename) throws IOException {
                out.writeInt(status);
                out.writeUTF(statusLine);
                if(filename != null) {
                    out.writeUTF(HEADER_FILENAME);
                    out.writeUTF(filename);
                }
            }

            public MultiFileResponseHeaderWriter contentType(String contentType) throws IOException {
                out.writeUTF(HttpHeaders.CONTENT_TYPE);
                out.writeUTF(contentType);
                return this;
            }

            public MultiFileResponseHeaderWriter contentLength(long length) throws IOException {
                out.writeUTF(HttpHeaders.CONTENT_LENGTH);
                out.writeUTF(length + "");
                return this;
            }

            public MultiFileResponseHeaderWriter lastModified(long lastModified) throws IOException {
                out.writeUTF(HttpHeaders.LAST_MODIFIED);
                out.writeUTF(HttpUtil.format(new Date(lastModified)));
                return this;
            }

            public void write() throws IOException {
                out.writeUTF("\n");
            }
        }

        public void write(FileRecord f) throws IOException {

            if(f.getFile() == null) {
                new MultiFileResponseHeaderWriter(HttpStatus.SC_NOT_FOUND, "Not Found", f.getName())
                        .contentLength(0)
                        .write();
            } else {
                char type = FileRecord.typeOf(f.getFile());
                if(type == FileRecord.TYPE_DIRECTORY) {

                    try {
                        long lastModified = f.getFile().lastModified();

                        new MultiFileResponseHeaderWriter(HttpStatus.SC_OK, "OK", f.getName())
                                .contentLength(0)
                                .contentType(CONTENT_TYPE_DIRECTORY)
                                .lastModified(lastModified)
                                .write();
                    } catch(IOException e) {
                        log.error("can't read last modified date of directory " + f.getFile() + ": " + ExceptionUtils.getMessage(e));
                        new MultiFileResponseHeaderWriter(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error (can't read last modified date)", f.getName())
                                .contentLength(0)
                                .write();
                    }
                } else if(type == FileRecord.TYPE_FILE) {
                    FileInputStream fis;
                    long lastModified;
                    long length;
                    try {
                        lastModified = f.getFile().lastModified();
                        length = f.getFile().length();
                        fis = new FileInputStream(f.getFile());
                    } catch(FileNotFoundException e) {
                        log.error("file not found: " + f.getFile());
                        new MultiFileResponseHeaderWriter(HttpStatus.SC_NOT_FOUND, "Not Found", f.getName())
                                .contentLength(0)
                                .write();
                        return;
                    }

                    new MultiFileResponseHeaderWriter(HttpStatus.SC_OK, "OK", f.getName())
                            .contentType(ContentType.APPLICATION_OCTET_STREAM.getMimeType())
                            .lastModified(lastModified)
                            .contentLength(length)
                            .write();

                    // XXX input stream could have less or more bytes than length
                    // if file was changed after reading length, use chunked encoding

                    try {
                        IOUtils.copy(fis, out);
                        fis.close();
                    } catch(Throwable e) {
                        log.error("exception writing file " + f.getFile().getName() + ", aborting", e);
                        // IO Exceptions reading file lead to aborted response, client
                        // can't recover and read next file without chuncked encoding
                        throw e;
                    }
                } else {
                    log.error("cannot encode file that is not a file or directory: " + f.getFile());
                    new MultiFileResponseHeaderWriter(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error (not a file or directory)", f.getName())
                            .contentLength(0)
                            .write();
                }
            }
        }

        @Override
        public void close() throws IOException {
            new MultiFileResponseHeaderWriter(HttpStatus.SC_NO_CONTENT, "No more content", null)
                    .contentLength(0)
                    .write();
            out.close();
        }
    }
}
