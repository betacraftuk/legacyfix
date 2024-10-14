package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.util.SkinUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class SkinHandler extends HandlerBase {
    private static final String DOMAIN_PATTERN = "(http:\\/\\/(skins\\.minecraft\\.net|(www\\.)?minecraft\\.net|s3\\.amazonaws\\.com)";

    private static final Pattern SKIN_PATTERN = Pattern.compile(DOMAIN_PATTERN + "\\/(skin|MinecraftSkins)\\/(.+)?\\.png)");
    private static final Pattern CAPE_PATTERN = Pattern.compile(DOMAIN_PATTERN + "\\/(cloak\\/get\\.jsp\\?user=|MinecraftCloaks\\/)([a-zA-Z0-9_+]+)?(?:\\.png)?)");

    private boolean isCapeRequest = false;
    private Pattern patternUsed;

    public SkinHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);
        this.patternUsed = patternUsed;

        if (CAPE_PATTERN.equals(patternUsed))
            this.isCapeRequest = true;
    }

    public void connect() throws IOException {
        Matcher matcher = this.patternUsed.matcher(this.getURLString());
        if (!matcher.find()) {
            LFLogger.error("JoinServerHandler", "No match for skin URL :(");
            return;
        }

        String username = matcher.group(5);

        if (this.isCapeRequest) {
            this.stream = new ByteArrayInputStream(
                    SkinUtils.getFixedCape(
                            SkinUtils.getSkin(username)
                    )
            );
        } else {
            this.stream = new ByteArrayInputStream(
                    SkinUtils.getFixedSkin(
                            SkinUtils.getSkin(username)
                    )
            );
        }
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                SKIN_PATTERN,
                CAPE_PATTERN
        );
    }
}
