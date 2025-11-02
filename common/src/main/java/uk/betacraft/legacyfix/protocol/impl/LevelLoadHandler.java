package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.Logger;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class LevelLoadHandler extends LevelHandlerBase {
    private static final Pattern LEVEL_LOAD_PATTERN = Pattern.compile("(http:\\/\\/(www\\.)?minecraft\\.net(:(.+)?)?\\/level\\/load\\.html\\?id=(.+)?&user=(.+)?)");

    public LevelLoadHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);

        try {
            this.prepare();
        } catch (Throwable t) {
            Logger.error("Failed to handle level load request");
            Logger.error("LevelLoadHandler", t);
        }
    }

    private void prepare() throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(data);

        try {
            Matcher matcher = LEVEL_LOAD_PATTERN.matcher(this.getURLString());
            if (!matcher.find()) {
                Logger.error("LevelLoadHandler", "No match for load level URL :(");
                throw new MalformedURLException("Query is missing \"id\" parameter");
            }

            int levelId = Integer.parseInt(matcher.group(5));
            File levels = new File(LevelListHandler.LEVELS_DIR_PATH);
            File level = new File(levels, "level" + levelId + ".dat");
            if (!level.exists()) {
                throw new FileNotFoundException("Level doesn't exist");
            }

            DataInputStream in = new DataInputStream(new FileInputStream(level));
            byte[] levelData = readStream(in);

            output.writeUTF("ok");
            output.write(levelData);
            output.close();
        } catch (Exception e) {
            Logger.error("LevelLoadHandler", e);

            try {
                Thread.sleep(100L); // Needs some sleep because it won't display error message if it's too fast
            } catch (InterruptedException ignored) {
            }

            ByteArrayOutputStream errorData = new ByteArrayOutputStream();
            DataOutputStream errorOutput = new DataOutputStream(errorData);

            errorOutput.writeUTF("error");
            errorOutput.writeUTF(e.getMessage());
            errorOutput.close();

            this.inputStream = new ByteArrayInputStream(errorData.toByteArray());
        }

        this.inputStream = new ByteArrayInputStream(data.toByteArray());
    }

    public static byte[] readStream(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        return buffer.toByteArray();
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
            LEVEL_LOAD_PATTERN
        );
    }
}
