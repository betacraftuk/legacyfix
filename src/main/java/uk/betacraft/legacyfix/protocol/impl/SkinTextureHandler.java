package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.LFLogger;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class SkinTextureHandler extends HandlerBase {
    private static List<Pattern> texturePatterns = new ArrayList<Pattern>();
    private static Map<String, byte[]> skinData = new HashMap<String, byte[]>();

    public SkinTextureHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);

        this.inputStream = new ByteArrayInputStream(skinData.get(this.getURLString()));
    }

    public static void addSkin(String skinUrl, byte[] data) {
        if (!texturePatterns.contains(skinUrl)) {
            texturePatterns.add(Pattern.compile(skinUrl));
        }

        if (skinData.containsKey(skinUrl)) {
            skinData.remove(skinUrl);
        }

        skinData.put(skinUrl, data);

        LFLogger.debug("Cached fixed skin: " + skinUrl);
    }

    public static List<Pattern> regexPatterns() {
        return texturePatterns;
    }
}
