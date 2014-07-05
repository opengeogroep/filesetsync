
package nl.opengeogroep.filesetsync.protocol;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    public boolean isDirectory() {
        return getContentType().equals(CONTENT_TYPE_DIRECTORY);
    }
    
    public final long getContentLength() {
        return Long.parseLong(headers.get(HttpHeaders.CONTENT_LENGTH));
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
