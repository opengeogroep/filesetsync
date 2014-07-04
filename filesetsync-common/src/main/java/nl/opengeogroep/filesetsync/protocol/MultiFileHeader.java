
package nl.opengeogroep.filesetsync.protocol;

import java.io.DataInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import nl.opengeogroep.filesetsync.util.HttpUtil;
import org.apache.http.HttpHeaders;

/**
 *
 * @author Matthijs Laan
 */
public class MultiFileHeader {
    final int status;
    final String statusLine;
    final Map<String,String> headers = new HashMap();

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
    }

    public long getContentLength() {
        return Long.parseLong(headers.get(HttpHeaders.CONTENT_LENGTH));
    }

    public String getContentType() {
        return headers.get(HttpHeaders.CONTENT_TYPE);
    }

    public Date getLastModified() throws ParseException {
        String s = headers.get(HttpHeaders.LAST_MODIFIED);
        return HttpUtil.parseDate(s);
    }
}
