
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

    private final DataInputStream in;
    private final int version;
    private MultiFileHeader next, previous;

    private IOException exception;

    public MultiFileDecoder(InputStream in, int version) {
        this.in = new DataInputStream(in);
        this.version = version;
    }

    public DataInputStream getDataStream() {
        return in;
    }

    /* hasNext() and next() can't throw exceptions... */
    public IOException getIOException() {
        return exception;
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
            next = new MultiFileHeader(in, version);
            return next.status != HttpStatus.SC_NO_CONTENT;
        } catch(EOFException e) {
            log.error("Unexpected EOF (No content header expected)");
            exception = e;
            return false;
        } catch(IOException e) {
            log.error("Error decoding multifile stream", e);
            exception = e;
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