package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.LegacyFixAgent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class Minecraft1_6AvailableHandler extends HandlerBase {
    private static final Pattern FLAG_PATTERN = Pattern.compile("(http:\\/\\/assets\\.minecraft\\.net\\/1_6_has_been_released\\.flag)");

    private static final boolean SHOW_NOTICE = "true".equals(LegacyFixAgent.getSetting("lf.showNotice", "false"));

    public Minecraft1_6AvailableHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);

        byte[] response;
        if (SHOW_NOTICE) {
            response = "https://web.archive.org/web/20130702232237if_/https://mojang.com/2013/07/minecraft-the-horse-update/".getBytes();
        } else {
            response = new byte[0];
        }

        this.stream = new ByteArrayInputStream(response);
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                FLAG_PATTERN
        );
    }
}
