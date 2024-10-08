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
public class IndevAntiPiracyHandler extends HandlerBase {
    private static final Pattern INDEV_ANTIPIRACY_PATTERN = Pattern.compile("(http:\\/\\/(www\\.)?minecraft\\.net(:(.+)?)?\\/\\?(.+)?)");

    public IndevAntiPiracyHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }

    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(
                ("true".equals(LegacyFixAgent.getSetting("lf.demo", "false")) ? "no" : "42069").getBytes()
        );
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                INDEV_ANTIPIRACY_PATTERN
        );
    }
}
