package nl.opengeogroep.filesetsync.client;

import nl.opengeogroep.filesetsync.client.util.L10n;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import static javax.swing.JOptionPane.*;
import javax.swing.UIManager;
import javax.xml.bind.JAXBException;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.SAXParseException;

public final class Main {
    private static final Log log = LogFactory.getLog(Main.class);
    
    public static void main(String[] args) {
        
        try {
            UIManager.setLookAndFeel(
                UIManager.getSystemLookAndFeelClassName());        
        } catch(Exception e) {
        }
        
        // Determine base path 
        // Also works for Windows: the URL always contains forward slashes.
        
        String path = Main.class.getResource("Main.class").toString();
        if(path.startsWith("file:")) {
            // Not in a JAR: file:/some/path/classes/nl/opengeogroep/filesetsync/client/Main.class
            // Pick directory above directory containing top level package
            path = path.substring("file:".length());
            int i = path.indexOf("/nl/opengeogroep/filesetsync/client/Main.class");
            path = path.substring(0, i);
            i = path.lastIndexOf("/");
            path = path.substring(0, i);
        } else {
            // In a JAR: jar:file:/some/path/filesetsync-client-1.0-SNAPSHOT.jar!/nl/opengeogroep/filesetsync/client/Main.class
            path = path.substring("jar:file:".length());
            int i = path.indexOf(".jar!/");
            // Path to JAR file
            path = path.substring(0, i+4);
            i = path.lastIndexOf("/");
            // Path to directory of JAR file
            path = path.substring(0, i);
        }
        
        String configFile = path + File.separator + "filesetsync-config.xml";
        try {
            SyncConfig.load(path, configFile);
        } catch(IOException e) {
            showMessageDialog(null, MessageFormat.format(L10n.s("configfile.io"),
                    configFile,
                    ExceptionUtils.getMessage(e)), L10n.s("error"), ERROR_MESSAGE);
            System.exit(1);
        } catch(JAXBException e) {
            String message = ExceptionUtils.getMessage(e) + ExceptionUtils.getRootCauseMessage(e);
            if(e.getCause() != null && e.getCause() instanceof SAXParseException) {
                message = message + " (line " + ((SAXParseException)e.getCause()).getLineNumber() + ")";
            }
            showMessageDialog(null, MessageFormat.format(L10n.s("configfile.bad"),
                    configFile,
                    message)); 
            System.exit(1);
        }
        
        log.info(SyncConfig.getInstance());
        
        File varDir = new File(SyncConfig.getInstance().getVarDir());   
        if(!varDir.exists()) {
            varDir.mkdirs();
        }   
        
        SyncJobStatePersistence.initialize();
        SyncRunner.getInstance().start();
    }
}
