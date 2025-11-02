package uk.betacraft.legacyfix.tweaker.logger;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LFLogger extends Logger {
    public LFLogger(Logger old) {
        super(old.getName(), old.getResourceBundleName());
    }

    @Override
    public void log(Level level, String message, Object ...data) {
        if (message.contains("but that path is defined and not secure")) {
            return;
        }

        super.log(level, message, data);
    }
}
