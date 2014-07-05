/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.opengeogroep.filesetsync;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import nl.opengeogroep.filesetsync.protocol.Protocol;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author matthijsln
 */
public class FileRecordListDirectoryIteratorTest {

    private final List<FileRecord>[] filelists = new List[2];
    private final String[] expOutputs = new String[2];


    public FileRecordListDirectoryIteratorTest() {
    }

    @Before
    public void setUpClass() throws IOException {

        filelists[0] = Protocol.decodeFilelist(getClass().getResourceAsStream("filelist1.txt"));
        expOutputs[0] = IOUtils.toString(getClass().getResourceAsStream("filelist1-iterated.txt")).replaceAll("\n", "");
        filelists[1] = Protocol.decodeFilelist(getClass().getResourceAsStream("filelist2.txt"));
        expOutputs[1] = IOUtils.toString(getClass().getResourceAsStream("filelist2-iterated.txt")).replaceAll("\n", "");
    }

    private String output(FileRecordListDirectoryIterator it) {
        String output = "";
        for(List<FileRecord> dir: it) {
            for(FileRecord fr: dir) {
                output += fr.getType() + "|" + fr.getName() + ",";
            }
            output += "#";
        }
        return output;
    }

    @org.junit.Test
    public void testFilelist1() {

        FileRecordListDirectoryIterator it = new FileRecordListDirectoryIterator(filelists[0]);
        String output = output(it);

        assertEquals(expOutputs[0], output);
    }

    @org.junit.Test
    public void testFilelist2() {

        FileRecordListDirectoryIterator it = new FileRecordListDirectoryIterator(filelists[1]);
        String output = output(it);

        assertEquals(expOutputs[1], output);
    }
}
