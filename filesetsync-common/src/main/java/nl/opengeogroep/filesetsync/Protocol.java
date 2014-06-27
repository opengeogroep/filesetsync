
package nl.opengeogroep.filesetsync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author Matthijs Laan
 */
public class Protocol {
    public static List<FileRecord> decodeFilelist(InputStream in) throws IOException {
        // Poorly-encoded text decoding
        
        List<FileRecord> l = new ArrayList();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String line = br.readLine();
        if(line == null || !"filesetsync:filelist".equals(line)) {
            throw new IOException("Wrong filelist format returned, bad server URL?");
        }
        while((line = br.readLine()) != null) {
            String[] s = line.split("|");
            FileRecord r = new FileRecord();
            r.setType(s[0].charAt(0));
            r.setName(s[1]);
            r.setSize(Long.parseLong(s[2]));
            r.setLastModified(new Date(Long.parseLong(s[3])));
            r.setChecksum(s[4]);
            l.add(r);
        }
        return l;
    }    
}
