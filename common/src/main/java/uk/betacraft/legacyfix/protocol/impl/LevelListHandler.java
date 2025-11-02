package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.LegacyFixLauncher;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class LevelListHandler extends LevelHandlerBase {
    private static final Pattern LEVEL_LIST_PATTERN = Pattern.compile("(http:\\/\\/(www\\.)?minecraft\\.net(:(.+)?)?\\/listmaps\\.jsp\\?user=(.+)?)");

    protected static final String LEVELS_DIR_PATH = System.getProperty("lf.levelDir", LegacyFixLauncher.getGameDir() + "/levels");

    public static final String EMPTY_LEVEL = "-";

    public LevelListHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);

        this.prepare();
    }

    private void prepare() {
        String levels = "";
        for (int i = 0; i < 5; i++) {
            levels += EMPTY_LEVEL + ";";
        }

        File levelsFolder = new File(LEVELS_DIR_PATH);
        File levelNames = new File(levelsFolder, "levels.txt");
        if (!levelNames.exists()) {
            this.inputStream = new ByteArrayInputStream(levels.getBytes());
        } else {
            // TODO: ignore this exception maybe? does not seem logical to ever occur
            try {
                this.inputStream = new FileInputStream(levelNames);
            } catch (FileNotFoundException e) {
                Logger.error("File not found but exists?");
                Logger.error("LevelListHandler.prepare", e);
            }
        }
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
            LEVEL_LIST_PATTERN
        );
    }
}
