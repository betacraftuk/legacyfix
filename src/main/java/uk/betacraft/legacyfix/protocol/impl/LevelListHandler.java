package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.LegacyFixAgent;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class LevelListHandler extends HandlerBase {
    public static final String EMPTY_LEVEL = "-";
    protected static final String LEVELS_DIR_PATH = System.getProperty("lf.levelsDir", LegacyFixAgent.getGameDir() + "/levels");
    private static final Pattern LEVEL_LIST_PATTERN = Pattern.compile("(http:\\/\\/(www\\.)?minecraft\\.net(:(.+)?)?\\/game\\/listmaps\\.jsp\\?user=(.+)?)");

    public LevelListHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }

    public InputStream getInputStream() throws IOException {
        String levels = "";
        for (int i = 0; i < 5; i++) {
            levels += EMPTY_LEVEL + ";";
        }

        File levelsFolder = new File(LEVELS_DIR_PATH);
        File levelNames = new File(levelsFolder, "levels.txt");
        if (!levelNames.exists()) {
            return new ByteArrayInputStream(levels.getBytes());
        }

        return new FileInputStream(levelNames);
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                LEVEL_LIST_PATTERN
        );
    }
}
