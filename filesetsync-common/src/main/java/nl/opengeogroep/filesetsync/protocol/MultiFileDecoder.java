
package nl.opengeogroep.filesetsync.protocol;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpStatus;

/**
 *
 * @author Matthijs Laan
 */
public class MultiFileDecoder implements Iterator<MultiFileHeader>, Iterable<MultiFileHeader>, AutoCloseable {
    private static final Log log = LogFactory.getLog(MultiFileDecoder.class);

    final private DataInputStream in;

    private MultiFileHeader next, previous;

    public MultiFileDecoder(InputStream in) {
        this.in = new DataInputStream(in);
    }

    public DataInputStream getDataStream() {
        return in;
    }

    @Override
    public boolean hasNext() {

        if(next != null) {
            return true;
        }

        if(previous != null) {
            // Make sure stream is positioned at next header by reading body
            try {
                long skipped;
                do {
                    skipped = previous.getBody().skip(Long.MAX_VALUE);
                } while(skipped > 0);
            } catch(IOException e) {
                log.error("Error skipping multifile body", e);
            }
        }
        
        try {
            next = new MultiFileHeader(in);
            if(next.status == HttpStatus.SC_NO_CONTENT) {
                return false;
            }
            return true;
        } catch(EOFException e) {
            log.error("Unexpected EOF (No content header expected)");
            return false;
        } catch(IOException e) {
            log.error("Error decoding multifile stream", e);
            return false;
        }
    }

    @Override
    public MultiFileHeader next() {
        if(!hasNext()) {
            throw new NoSuchElementException();
        }

        MultiFileHeader header = next;
        previous = header;
        next = null;
        return header;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public Iterator<MultiFileHeader> iterator() {
        return this;
    }
}