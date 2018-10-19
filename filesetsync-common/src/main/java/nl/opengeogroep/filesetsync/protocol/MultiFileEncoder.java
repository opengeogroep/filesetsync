
package nl.opengeogroep.filesetsync.protocol;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import nl.opengeogroep.filesetsync.FileRecord;
import nl.opengeogroep.filesetsync.util.HttpUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;

/**
 *
 * @author Matthijs Laan
 */
public class MultiFileEncoder implements Closeable, AutoCloseable {
    public static final String MULTIFILE_MIME_TYPE = "application/x-filesetsync-multifile";
    static final String CONTENT_TYPE_DIRECTORY = "application/x-filesetsync-directory";
    static final String HEADER_FILENAME = "X-Filesetsync-Filename";

    private final DataOutputStream out;
    private final HttpUtil httpUtil = new HttpUtil();
    private final int version;

    final Log log;

    public MultiFileEncoder(OutputStream out, int version, Log log) {
        this.out = new DataOutputStream(out);
        this.version = version;
        this.log = log;
    }

    private void writeFileHeader(int status, String statusLine, String filename) throws IOException {
        out.writeInt(status);
        out.writeUTF(statusLine);
        if(filename != null) {
            out.writeUTF(HEADER_FILENAME);
            out.writeUTF(filename);
        }
    }

    private void writeHttpHeader(String name, String value) throws IOException {
        out.writeUTF(name);
        out.writeUTF(value);
    }

    private void writeHttpDateHeader(String name, long date) throws IOException {
        if(version > 1) {
            writeHttpHeader(name, date + "");
        } else {
            writeHttpHeader(name, httpUtil.formatDate(new Date(date)));
        }
    }

    private void writeFileHeaderEnd() throws IOException {
        out.writeUTF("\n");
    }

    public void write(FileRecord f) throws IOException {

        if(f.getFile() == null) {
            writeFileHeader(HttpStatus.SC_NOT_FOUND, "Not Found", f.getName());
            writeHttpHeader(HttpHeaders.CONTENT_LENGTH, "0");
            writeFileHeaderEnd();
        } else {
            char type = FileRecord.typeOf(f.getFile());
            if(type == FileRecord.TYPE_DIRECTORY) {
                writeFileHeader(HttpStatus.SC_OK, "OK", f.getName());
                writeHttpHeader(HttpHeaders.CONTENT_LENGTH, "0");
                writeHttpHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_DIRECTORY);
                writeHttpDateHeader(HttpHeaders.LAST_MODIFIED, f.getFile().lastModified());
                writeFileHeaderEnd();
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

                    writeFileHeader(HttpStatus.SC_NOT_FOUND, "Not Found", f.getName());
                    writeHttpHeader(HttpHeaders.CONTENT_LENGTH, "0");
                    writeFileHeaderEnd();
                    return;
                }

                writeFileHeader(HttpStatus.SC_OK, "OK", f.getName());
                writeHttpHeader(HttpHeaders.CONTENT_LENGTH, length + "");
                writeHttpHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_OCTET_STREAM.getMimeType());
                writeHttpDateHeader(HttpHeaders.LAST_MODIFIED, lastModified);
                writeFileHeaderEnd();

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

                writeFileHeader(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error (not a file or directory)", f.getName());
                writeHttpHeader(HttpHeaders.CONTENT_LENGTH, "0");
                writeFileHeaderEnd();
            }
        }
    }

    @Override
    public void close() throws IOException {
        writeFileHeader(HttpStatus.SC_NO_CONTENT, "No more content", null);
        writeHttpHeader(HttpHeaders.CONTENT_LENGTH, "0");
        writeFileHeaderEnd();
        out.close();
    }
}
