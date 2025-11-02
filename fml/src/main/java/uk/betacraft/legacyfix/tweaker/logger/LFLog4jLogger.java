package uk.betacraft.legacyfix.tweaker.logger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Logger;

public class LFLog4jLogger extends Logger {
    public LFLog4jLogger(Logger old) {
        super(old.getContext(), old.getName(), old.getMessageFactory());
    }

    @Override
    public void log(Level level, String message) {
        if (message.contains("but that path is defined and not secure")) {
            return;
        }

        super.log(level, message);
    }
}
