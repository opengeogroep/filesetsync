
package nl.opengeogroep.filesetsync.protocol;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class MultiFileDecoder implements Iterator<MultiFileHeader>, AutoCloseable {
    private static final Log log = LogFactory.getLog(MultiFileDecoder.class);

    final private DataInputStream in;

    private MultiFileHeader next;

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

        try {
            next = new MultiFileHeader(in);
            return true;
        } catch(EOFException e) {
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
        next = null;
        return header;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws Exception {
        in.close();
    }
}