
package nl.opengeogroep.filesetsync.protocol;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import nl.opengeogroep.filesetsync.FileRecord;

/**
 *
 * @author Matthijs Laan
 */
public class Protocol {

    public static final String FILELIST_ENCODING = "UTF-8";
    public static final String FILELIST_MIME_TYPE = "application/x-filesetsync-filelist";
    static final String FILELIST_HEADER_START = "filesetsync:filelist:start";
    static final String FILELIST_HEADER_END = "filesetsync:filelist:end";

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

}
