
package nl.opengeogroep.filesetsync.protocol;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import nl.opengeogroep.filesetsync.FileRecord;
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
public class MultiFileEncoder implements AutoCloseable {
    public static final String MULTIFILE_MIME_TYPE = "application/x-filesetsync-multifile";
    static final String CONTENT_TYPE_DIRECTORY = "application/x-filesetsync-directory";
    static final String HEADER_FILENAME = "X-Filesetsync-Filename";

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
            out.writeUTF(HttpUtil.formatDate(new Date(lastModified)));
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
