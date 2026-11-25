package uk.betacraft.legacyfix.tweaker.logger;

import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LFLogger extends Logger {
    public LFLogger(Logger old) {
        super(old.getName(), old.getResourceBundleName());
        this.setParent(old);
        this.setUseParentHandlers(true);
    }

    @Override
    public void log(LogRecord record) {
        String message = record.getMessage();
        if (message != null && message.contains("but that path is defined and not secure")) {
            return;
        }

        super.log(record);
    }
}
