
package nl.opengeogroep.filesetsync;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;

/**
 *
 * @author Matthijs Laan
 */
public class Protocol {

    public static final String FILELIST_ENCODING = "UTF-8";

    public static final String FILELIST_MIME_TYPE = "application/x-filesetsync-filelist";
    public static final String MULTIFILE_MIME_TYPE = "application/x-filesetsync-multifile";

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

    public static class BufferedFileRecordEncoder implements AutoCloseable {
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

        @Override
        public void close() throws IOException {
            writer.append("filesetsync:filelist:end\n");
            writer.close();
        }
    }

    public static class MultiFileEncoder implements AutoCloseable {
        final private DataOutputStream out;

        boolean headerWritten = false;

        final Log log;

        public MultiFileEncoder(OutputStream out, Log log) {
            this.out = new DataOutputStream(out);
            this.log = log;
        }

        public void write(FileRecord f) throws IOException {
            if(!headerWritten) {
                out.writeUTF("filesetsync:multifile:start");
                headerWritten = true;
            }
            out.writeUTF(f.getName());
            if(f.getFile() == null) {
                out.writeBoolean(false);
            } else {
                char type = FileRecord.typeOf(f.getFile());
                if(type == FileRecord.TYPE_DIRECTORY) {
                    out.writeBoolean(true);
                    out.writeChar(type);
                    out.writeLong(f.getFile().lastModified());
                } else if(type == FileRecord.TYPE_FILE) {
                    FileInputStream fis;
                    try {
                        fis = new FileInputStream(f.getFile());
                    } catch(FileNotFoundException e) {
                        log.error("file not found: " + f.getFile());
                        out.writeBoolean(false);
                        return;
                    }
                    out.writeBoolean(true);
                    out.writeChar(type);
                    out.writeLong(f.getFile().lastModified());
                    out.writeLong(f.getFile().length());

                    // XXX input stream could have less or more bytes than length
                    // use chunked encoding
                    IOUtils.copy(fis, out);
                    fis.close();
                } else {
                    log.error("cannot encode file that is not a file or directory: " + f.getFile());
                    out.writeBoolean(false);
                }
            }
        }

        @Override
        public void close() throws IOException {
            out.writeUTF("filesetsync:multifile:end");
            out.close();
        }
    }
}
