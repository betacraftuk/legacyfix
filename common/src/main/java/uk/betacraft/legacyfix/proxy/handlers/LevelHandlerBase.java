package uk.betacraft.legacyfix.proxy.handlers;

import java.net.URL;
import java.util.regex.Pattern;

public abstract class LevelHandlerBase extends HandlerBase {
    public static String levelToken = null;

    protected LevelHandlerBase(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }
}
