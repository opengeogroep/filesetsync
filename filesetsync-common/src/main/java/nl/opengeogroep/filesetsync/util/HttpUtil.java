
package nl.opengeogroep.filesetsync.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.http.protocol.HttpDateGenerator;

/**
 *
 * @author Matthijs Laan
 */
public class HttpUtil {
    private DateFormat format = null;

    private DateFormat getFormat() {
        if(format == null) {
            format = new SimpleDateFormat(HttpDateGenerator.PATTERN_RFC1123, Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        return format;
    }

    public String formatDate(Date date) {
        return getFormat().format(date);
    }

    public Date parseDate(String dateString) throws ParseException {
        return getFormat().parse(dateString);
    }
}
