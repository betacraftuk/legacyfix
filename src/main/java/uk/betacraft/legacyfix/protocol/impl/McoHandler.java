package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.LFLogger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class McoHandler extends HandlerBase {
    private static final Pattern MCO_PATTERN = Pattern.compile("(https:\\/\\/mcoapi\\.minecraft\\.net\\/(.+)?)");

    public McoHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }

    @Override
    public int getResponseCode() {
        return 200;
    }

    @Override
    public InputStream getInputStream() {
        this.stream = new ByteArrayInputStream("false".getBytes());

        return this.stream;
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                MCO_PATTERN
        );
    }
}
