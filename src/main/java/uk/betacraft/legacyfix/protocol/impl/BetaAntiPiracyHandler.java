package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.LegacyFixAgent;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class BetaAntiPiracyHandler extends HandlerBase {
    private static final Pattern BETA_ANTIPIRACY_PATTERN = Pattern.compile("(http(s)?:\\/\\/login\\.minecraft\\.net\\/session\\?(.+)?)");

    public BetaAntiPiracyHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }

    public int getResponseCode() {
        return "true".equals(LegacyFixAgent.getSetting("lf.demo", "false")) ? 400 : 200;
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                BETA_ANTIPIRACY_PATTERN
        );
    }
}
