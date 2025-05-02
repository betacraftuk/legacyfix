package uk.betacraft.legacyfix.protocol.impl;

import java.net.URL;
import java.util.regex.Pattern;

public abstract class LevelHandlerBase extends HandlerBase {
    public static final String ONLINE_LEVEL_SERVER = System.getProperty("lf.levelServer", null);

    protected LevelHandlerBase(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }
}
