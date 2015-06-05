
package nl.opengeogroep.filesetsync.protocol;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import static nl.opengeogroep.filesetsync.protocol.MultiFileEncoder.CONTENT_TYPE_DIRECTORY;
import static nl.opengeogroep.filesetsync.protocol.MultiFileEncoder.HEADER_FILENAME;
import nl.opengeogroep.filesetsync.util.HttpUtil;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.http.HttpHeaders;

/**
 *
 * @author Matthijs Laan
 */
public class MultiFileHeader {
    final int status;
    final String statusLine;
    final Map<String,String> headers = new HashMap();

    final InputStream input;

    public MultiFileHeader(DataInputStream in) throws IOException {
        status = in.readInt();
        statusLine = in.readUTF();

        while(true) {
            String header = in.readUTF();
            if("\n".equals(header)) {
                break;
            }
            String value = in.readUTF();
            headers.put(header, value);
        }

        input = new BoundedInputStream(in, getContentLength());
    }

    public int getStatus() {
        return status;
    }

    public String getStatusLine() {
        return statusLine;
    }

    private String getExceptionDetails() {
        return String.format(" for file %s (status: %d %s)", getFilename(), status, statusLine);
    }

    public boolean isDirectory() throws IOException {
        String contentType = getContentType();

        if(contentType == null) {
            throw new IOException(String.format("Missing content type header" + getExceptionDetails()));
        }
        return contentType.equals(CONTENT_TYPE_DIRECTORY);
    }

    public final long getContentLength() throws IOException {
        String contentLength = headers.get(HttpHeaders.CONTENT_LENGTH);

        if(contentLength == null) {
            throw new IOException(String.format("No content length in header" + getExceptionDetails()));
        }
        try {
            return Long.parseLong(contentLength);
        } catch(NumberFormatException e) {
            throw new IOException(String.format("Invalid content length header \"%s\"" + getExceptionDetails()));
        }
    }

    public String getContentType() {
        return headers.get(HttpHeaders.CONTENT_TYPE);
    }

    public Date getLastModified() {
        String s = headers.get(HttpHeaders.LAST_MODIFIED);
        try {
            return HttpUtil.parseDate(s);
        } catch (ParseException ex) {
            return new Date();
        }
    }

    public String getFilename() {
        return headers.get(HEADER_FILENAME);
    }

    public InputStream getBody() {
        return input;
    }
}
