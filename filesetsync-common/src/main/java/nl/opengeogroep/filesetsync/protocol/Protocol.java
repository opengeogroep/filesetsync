
package nl.opengeogroep.filesetsync.protocol;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.opengeogroep.filesetsync.FileRecord;
import org.apache.commons.lang3.mutable.MutableInt;

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

    public static Iterable<FileRecord> decodeFileListIterable(final InputStream in, final String rootPath, final String subPath, final String regexp, final MutableInt noRegexpMatches) throws IOException {
        return new Iterable<FileRecord>() {
            @Override
            public Iterator<FileRecord> iterator() {
                return new FileListIterator(in, rootPath, subPath, regexp, noRegexpMatches);
            }
        };
    }

    public static class FileListIterator implements Iterator<FileRecord> {
        private BufferedReader br;
        private String rootPath, subPath;
        private FileRecord next = null;
        private int records = 0;
        private String regexp;
        private MutableInt noRegexpMatches;

        public FileListIterator(InputStream in, String rootPath, String subPath, String regexp, MutableInt noRegexpMatches) throws IllegalArgumentException {
            this.rootPath = rootPath;
            this.subPath = subPath;
            this.regexp = regexp;
            this.noRegexpMatches = noRegexpMatches == null ? new MutableInt() : noRegexpMatches;
            String line;
            try {
                this.br = new BufferedReader(new InputStreamReader(in, FILELIST_ENCODING));
                line = br.readLine();
            } catch(Exception ex) {
                throw new IllegalArgumentException(ex);
            }
            if(line == null || !FILELIST_HEADER_START.equals(line)) {
                throw new IllegalArgumentException("Wrong filelist format returned, bad server URL? Output: " + line);
            }
        }

        @Override
        public FileRecord next() {
            if(!hasNext()) {
                throw new NoSuchElementException();
            }
            FileRecord r = next;
            next = null;
            return r;
        }

        @Override
        public boolean hasNext() {
            if(next != null) {
                return true;
            }
            String line;
            while(true) {
                try {
                    line = br.readLine();
                } catch(IOException ex) {
                    throw new IllegalArgumentException(ex);
                }
                if(line == null || FILELIST_HEADER_END.equals(line)) {
                    try {
                        br.close();
                    } catch (IOException ex) {
                    }
                    return false;
                }
                String[] s = line.split("\\|");
                FileRecord r = new FileRecord();
                r.setType(s[0].charAt(0));
                r.setName(s[1]);
                r.setFile(new File(rootPath + File.separator + r.getName()));
                if(subPath.length() > 0) {
                    if(!r.getName().startsWith(subPath.substring(1))) {
                        continue;
                    }
                    if(subPath.length()-1 == r.getName().length()) {
                        r.setName(".");
                    } else {
                        r.setName(r.getName().substring(subPath.length()));
                    }
                }
                r.setSize(Long.parseLong(s[2]));
                r.setLastModified(Long.parseLong(s[3]));
                r.setHash("null".equals(s[4]) ? null : s[4]);
                next = r;

                if(regexp == null || r.getName().matches(regexp)) {
                    return true;
                } else {
                    noRegexpMatches.increment();
                }
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
