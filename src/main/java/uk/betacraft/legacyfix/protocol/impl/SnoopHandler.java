package uk.betacraft.legacyfix.protocol.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class SnoopHandler extends HandlerBase {
    private static final Pattern SNOOP_PATTERN = Pattern.compile("(http:\\/\\/snoop\\.minecraft\\.net\\/(.+)?)");

    public SnoopHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }

    @Override
    public InputStream getInputStream() {
        this.stream = new ByteArrayInputStream(new byte[0]);

        return this.stream;
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                SNOOP_PATTERN
        );
    }
}
