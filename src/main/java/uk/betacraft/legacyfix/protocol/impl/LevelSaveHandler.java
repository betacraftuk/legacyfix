package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.LFLogger;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class LevelSaveHandler extends HandlerBase {
    private static final Pattern LEVEL_SAVE_PATTERN = Pattern.compile("(http:\\/\\/(www\\.)?minecraft\\.net(:(.+)?)?\\/level\\/save\\.html)");

    ByteArrayOutputStream levelOutput = new ByteArrayOutputStream();

    public LevelSaveHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            File levels = new File(LevelListHandler.LEVELS_DIR_PATH);

            byte[] data = levelOutput.toByteArray();
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

            String username = in.readUTF();
            String sessionId = in.readUTF();
            String levelName = in.readUTF();

            byte levelId = in.readByte();
            int length = in.readInt();
            byte[] levelData = new byte[length];
            in.read(levelData);
            in.close();

            File level = new File(levels, "level" + levelId + ".dat");
            File levelNames = new File(levels, "levels.txt");

            int maxLevels = 5;
            String[] lvlNames = new String[maxLevels];
            for (int i = 0; i < maxLevels; i++) {
                lvlNames[i] = LevelListHandler.EMPTY_LEVEL;
            }

            if (levelNames.exists()) {
                FileInputStream levelNamesStream = new FileInputStream(levelNames);
                lvlNames = new String(LevelLoadHandler.readStream(levelNamesStream)).split(";");
                levelNamesStream.close();
            }
            lvlNames[levelId] = levelName;
            if (levelName.equals("---")) {
                level.delete();
                lvlNames[levelId] = LevelListHandler.EMPTY_LEVEL;
            } else {
                if (!levels.exists()) {
                    levels.mkdirs();
                }

                OutputStream fileOutput = new FileOutputStream(level);
                fileOutput.write(levelData);
                fileOutput.close();
            }

            FileOutputStream outputNames = new FileOutputStream(levelNames);
            String lvls = "";
            for (int i = 0; i < maxLevels; i++) {
                lvls += lvlNames[i] + ";";
            }
            outputNames.write(lvls.getBytes());
            outputNames.close();
        } catch (Exception e) {
            LFLogger.error("LevelSaveHandler", e);

            try {
                // Needs some sleep because it won't display the error message if it's too fast
                Thread.sleep(100L);
            } catch (InterruptedException ignored) {
            }

            String err = "error\n" + e.getMessage();
            return new ByteArrayInputStream(err.getBytes());
        }
        return new ByteArrayInputStream("ok".getBytes());
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return levelOutput;
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                LEVEL_SAVE_PATTERN
        );
    }
}
