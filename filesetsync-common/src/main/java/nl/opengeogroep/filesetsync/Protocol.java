
package nl.opengeogroep.filesetsync;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Matthijs Laan
 */
public class Protocol {
    public static final String FILELIST_ENCODING = "UTF-8";

    public static List<FileRecord> decodeFilelist(InputStream in) throws IOException {
        // Poorly-encoded text decoding

        List<FileRecord> l = new ArrayList();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, FILELIST_ENCODING));
        String line = br.readLine();
        if(line == null || !"filesetsync:filelist".equals(line)) {
            throw new IOException("Wrong filelist format returned, bad server URL? Output: " + line);
        }
        while((line = br.readLine()) != null) {
            if("filesetsync:filelist:end".equals(line)) {
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

    public static class BufferedFileRecordEncoder {
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
                writer.append("filesetsync:filelist\n");
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

        public void flush() throws IOException {
            writer.append("filesetsync:filelist:end\n");
            writer.flush();
        }
    }
}
