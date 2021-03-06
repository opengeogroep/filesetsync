package nl.opengeogroep.filesetsync.client;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Properties;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import javax.swing.UIManager;
import javax.xml.bind.JAXBException;
import nl.opengeogroep.filesetsync.client.config.Plugin;
import nl.opengeogroep.filesetsync.client.config.Property;
import nl.opengeogroep.filesetsync.client.config.SyncConfig;
import nl.opengeogroep.filesetsync.client.plugin.api.PluginContext;
import nl.opengeogroep.filesetsync.client.plugin.api.PluginInterface;
import nl.opengeogroep.filesetsync.client.util.L10n;
import nl.opengeogroep.filesetsync.client.util.Version;
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

        String path, configFile;

        if(args.length == 2 && "-config".equals(args[0])) {
            configFile = args[1];
            path = new File(configFile).getAbsoluteFile().getParent();
        } else {
            // Determine base path
            // Also works for Windows: the URL always contains forward slashes.

            path = Main.class.getResource("Main.class").toString();
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

            configFile = path + File.separator + "filesetsync-config.xml";
        }

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

        Properties props = new Properties();
        for(Property p: SyncConfig.getInstance().getProperties()) {
            if(p.getValue() != null) {
                props.setProperty(p.getName(), p.getValue().replace("${var}", SyncConfig.getInstance().getVarDir()));
            }
        }
        org.apache.log4j.PropertyConfigurator.configure(props);

        log.info(String.format("%s %s starting (git commit %s, built at %s)",
                Version.getProperty("project.name"),
                Version.getProjectVersion(),
                Version.getProperty("git.commit.id"),
                Version.getProperty("git.build.time")
        ));

        StringWriter sw = new StringWriter();
        Version.getProperties().list(new PrintWriter(sw));
        log.trace(sw.toString());

        log.trace(SyncConfig.getInstance());

        File varDir = new File(SyncConfig.getInstance().getVarDir());
        if(!varDir.exists()) {
            varDir.mkdirs();
        }

        Runtime.getRuntime().addShutdownHook(new Shutdown());

        SyncJobStatePersistence.initialize();

        PluginContext.initialize();

        // Load plugins
        for(Plugin plugin: SyncConfig.getInstance().getPlugins()) {
            log.info("Loading plugin class " + plugin.getClazz());
            Class c;
            try {
                c = Class.forName(plugin.getClazz());
            } catch(Exception e) {
                log.error("Exception loading plugin class " + plugin.getClazz(), e);
                continue;
            }
            PluginInterface pluginInstance;
            try {
                pluginInstance = (PluginInterface)c.getConstructor().newInstance();
            } catch(Exception e) {
                log.error("Exception instantiating plugin class " + plugin.getClazz(), e);
                continue;
            }
            props = new Properties();
            for(Property p: plugin.getProperties()) {
                if(p.getValue() != null) {
                    props.setProperty(p.getName(), p.getValue().replace("${var}", SyncConfig.getInstance().getVarDir()));
                }
            }

            if(log.isTraceEnabled()) {
                sw = new StringWriter();
                props.list(new PrintWriter(sw));
                log.debug("Initializing plugin with properties " + sw.toString());
            }
            try {
                pluginInstance.setPluginContext(PluginContext.getInstance());
                pluginInstance.configure(props);
            } catch(Exception e) {
                sw = new StringWriter();
                props.list(new PrintWriter(sw));
                log.error("Error configuring plugin of class " + plugin.getClazz() + " with properties " + sw.toString(), e);
            }
        }

        AppState.load();

        Object firstRun = AppState.getStats().get("first_run");
        if(firstRun == null) {
            AppState.getStats().put("first_run", System.currentTimeMillis());
        }

        AppState.addStatsValue("startup_count", 1);

        Reporting.reportClientStartup();

        SyncRunner.getInstance().start();
    }
}
