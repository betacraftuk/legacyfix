package uk.betacraft.legacyfix.proxy.handlers;

import java.net.URL;
import java.util.regex.Pattern;

public abstract class LevelHandlerBase extends HandlerBase {
    public static final String ONLINE_LEVEL_SERVER = System.getProperty("lf.proxy.levelServer", "https://betacraft.uk");
    public static String levelToken = null;

    protected LevelHandlerBase(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }
}
