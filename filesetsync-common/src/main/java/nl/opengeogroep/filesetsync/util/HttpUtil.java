
package nl.opengeogroep.filesetsync.util;

import java.text.DateFormat;
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
    private static final DateFormat format = new SimpleDateFormat(HttpDateGenerator.PATTERN_RFC1123, Locale.US);

    static {
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    public static String format(Date date) {
        return format.format(date);
    }
}
