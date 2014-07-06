
package nl.opengeogroep.filesetsync.protocol;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import nl.opengeogroep.filesetsync.FileRecord;
import static nl.opengeogroep.filesetsync.protocol.Protocol.FILELIST_ENCODING;

/**
 *
 * @author Matthijs Laan
 */
public class BufferedFileListEncoder implements Closeable, AutoCloseable {
    private static final String FILELIST_HEADER_START = "filesetsync:filelist:start";
    private static final String FILELIST_HEADER_END = "filesetsync:filelist:end";

    private BufferedWriter writer = null;

    private boolean headerWritten = false;

    public BufferedFileListEncoder(OutputStream out) {
        try {
            this.writer = new BufferedWriter(new OutputStreamWriter(out, FILELIST_ENCODING));
        } catch (UnsupportedEncodingException ex) {
        }
    }

    public void write(FileRecord f) throws IOException {
        if(!headerWritten) {
            writer.append(FILELIST_HEADER_START + "\n");
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

    public BufferedFileListEncoder writeAll(Iterable<FileRecord> records) throws IOException {
        for(FileRecord fr: records) {
            write(fr);
        }
        return this;
    }

    @Override
    public void close() throws IOException {
        writer.append(FILELIST_HEADER_END + "\n");
        writer.close();
    }
}
