package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.patch.api.Patch;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.*;

public class Logger {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("LF");

    public static void info(String... lines) {
        log(Level.INFO, lines);
    }

    public static void error(Patch patch, Throwable t) {
        error(patch.getId(), t);
    }

    public static void error(String component, Throwable t) {
        error(component);
        // noinspection CallToPrintStackTrace
        t.printStackTrace();
    }

    public static void error(String... lines) {
        log(Level.SEVERE, lines);
    }

    public static void log(Level level, String... lines) {
        ensureConfigured();
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                LOGGER.log(level, lines[i]);
            } else {
                LOGGER.log(level,"    " + lines[i]);
            }
        }
    }

    public static void logList(String header, List<String> lines) {
        ensureConfigured();
        LOGGER.info(header);
        for (String line : lines) {
            LOGGER.info("    " + line);
        }
    }

    public static void debug(String... lines) {
        if (Agent.DEBUG) {
            log(Level.FINE, lines);
        }
    }

    private static void ensureConfigured() {
        if (LOGGER.getHandlers().length == 0) {
            LOGGER.setUseParentHandlers(false);
            LOGGER.setLevel(Level.ALL);

            Formatter formatter = new Formatter() {
                private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

                @Override
                public String format(LogRecord record) {
                    return
                        "[" + dateFormat.format(new Date(record.getMillis())) + "]" +
                        " [" + record.getLoggerName() + "/" + record.getLevel().getName() + "] " +
                        formatMessage(record) + "\n";
                }
            };

            StreamHandler handler = new StreamHandler(new FileOutputStream(FileDescriptor.out), formatter) {
                public synchronized void publish(LogRecord record) {
                    super.publish(record);
                    flush();
                }

                public synchronized void close() throws SecurityException {
                    flush();
                }
            };

            handler.setLevel(Level.ALL);
            LOGGER.addHandler(handler);
        }
    }

    static {
        ensureConfigured();
    }
}
