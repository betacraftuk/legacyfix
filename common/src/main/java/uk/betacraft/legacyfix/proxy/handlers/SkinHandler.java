package uk.betacraft.legacyfix.proxy.handlers;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.proxy.api.SkinOverrideApi;
import uk.betacraft.legacyfix.proxy.api.MinecraftApi;
import uk.betacraft.legacyfix.util.SkinUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
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

        if (CAPE_PATTERN.equals(patternUsed)) {
            this.isCapeRequest = true;
        }
    }

    public void connect() throws IOException {
        Matcher matcher = this.patternUsed.matcher(this.getURLString());
        if (!matcher.find()) {
            Logger.error("SkinHandler", "No match for skin URL");
            return;
        }

        String username = matcher.group(5);

        byte[] data;
        MinecraftApi.SkinData skinData = null;
        if (this.isCapeRequest) {
            if (SkinOverrideApi.ENABLED) {
                skinData = SkinOverrideApi.getCapeOverride(username);
            }

            if (skinData == null) {
                skinData = MinecraftApi.getSkin(username);
            }

            data = SkinUtils.getFixedCape(skinData);
        } else {
            if (SkinOverrideApi.ENABLED) {
                skinData = SkinOverrideApi.getSkinOverride(username);
            }

            if (skinData == null) {
                skinData = MinecraftApi.getSkin(username);
            }

            data = SkinUtils.getFixedSkin(skinData);
        }

        if (data != null) {
            this.inputStream = new ByteArrayInputStream(data);
        }
    }

    public static List<Pattern> regexPatterns() {
        if (Agent.hasSetting("lf.skin.disable")) {
            return Collections.emptyList();
        }

        return Arrays.asList(
            SKIN_PATTERN,
            CAPE_PATTERN
        );
    }
}
