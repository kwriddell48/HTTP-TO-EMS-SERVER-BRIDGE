package http.to.ems.messager;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point: starts the HTTP server that forwards requests to TIBCO EMS.
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final Logger ROOT_LOG = Logger.getLogger("http.to.ems.messager");
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_LOG_LEVEL = "INFO";

    public static void main(String[] args) {
        // Parse arguments
        int port = DEFAULT_PORT;
        String logLevel = DEFAULT_LOG_LEVEL;

        for (String arg : args) {
            if (arg.toUpperCase().startsWith("DEBUG=")) {
                logLevel = arg.substring(6).trim().toUpperCase();
            } else {
                try {
                    port = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid argument: " + arg);
                }
            }
        }

        // Set log level
        Level level = parseLogLevel(logLevel);
        setLogLevel(level);

        // Print startup banner
        printStartupBanner(port, logLevel);

        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] Main entry args.length={1}", new Object[]{java.time.Instant.now(), args != null ? args.length : 0});
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] Creating HttpServerApp port={1}", new Object[]{java.time.Instant.now(), port});
        }
        HttpServerApp server = new HttpServerApp(port);
        server.start();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] Server started", java.time.Instant.now());
        }
        System.out.println("HTTP server listening on port " + port);
    }

    private static Level parseLogLevel(String levelStr) {
        switch (levelStr) {
            case "ALL":     return Level.ALL;
            case "FINEST":  return Level.FINEST;
            case "FINER":   return Level.FINER;
            case "FINE":    return Level.FINE;
            case "CONFIG":  return Level.CONFIG;
            case "INFO":    return Level.INFO;
            case "WARNING": return Level.WARNING;
            case "SEVERE":  return Level.SEVERE;
            case "OFF":     return Level.OFF;
            default:
                System.err.println("Unknown log level: " + levelStr + ", using INFO");
                return Level.INFO;
        }
    }

    private static void setLogLevel(Level level) {
        ROOT_LOG.setLevel(level);
        // Also set console handler level
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(level);
            }
        }
        // Add console handler if none exists
        if (Logger.getLogger("").getHandlers().length == 0) {
            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(level);
            Logger.getLogger("").addHandler(ch);
        }
    }

    private static void printStartupBanner(int port, String logLevel) {
        String compileDateTime = getCompileDateTime();
        System.out.println("=======================================================");
        System.out.println("  HTTP to EMS Messager");
        System.out.println("  Compile Date/Time: " + compileDateTime);
        System.out.println("  Log Level: " + logLevel);
        System.out.println("=======================================================");
        System.out.println();
        System.out.println("Example command line:");
        System.out.println("  java -cp <classpath> http.to.ems.messager.Main [port] [DEBUG=level]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -cp target/classes;lib/* http.to.ems.messager.Main 8080");
        System.out.println("  java -cp target/classes;lib/* http.to.ems.messager.Main 8080 DEBUG=FINE");
        System.out.println("  java -cp target/classes;lib/* http.to.ems.messager.Main DEBUG=FINEST");
        System.out.println();
        System.out.println("Valid DEBUG levels: OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL");
        System.out.println("=======================================================");
        System.out.println();
    }

    private static String getCompileDateTime() {
        try {
            URL classUrl = Main.class.getResource("Main.class");
            if (classUrl != null) {
                if ("file".equals(classUrl.getProtocol())) {
                    File classFile = new File(classUrl.toURI());
                    long lastModified = classFile.lastModified();
                    if (lastModified > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        return sdf.format(new Date(lastModified));
                    }
                } else if ("jar".equals(classUrl.getProtocol())) {
                    String path = classUrl.getPath();
                    int idx = path.indexOf("!");
                    if (idx > 0) {
                        String jarPath = path.substring(5, idx); // remove "file:"
                        File jarFile = new File(jarPath);
                        if (jarFile.exists()) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            return sdf.format(new Date(jarFile.lastModified()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "unknown";
    }
}
