package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.util.AssetUtils;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class ResourceIndexHandler extends HandlerBase {
    private static final Pattern XML_INDEX_PATTERN = Pattern.compile("(http:\\/\\/(s3\\.amazonaws\\.com)\\/(MinecraftResources)\\/)");
    private static final Pattern TXT_INDEX_PATTERN = Pattern.compile("(http:\\/\\/((www\\.)?minecraft\\.net)\\/(resources)\\/)");

    private boolean isXmlRequest = false;

    public ResourceIndexHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);

        if (XML_INDEX_PATTERN.equals(patternUsed))
            this.isXmlRequest = true;
    }

    public InputStream getInputStream() throws IOException {
        String index;

        if (this.isXmlRequest) {
            index = AssetUtils.generateXmlIndex();
            if (LegacyFixAgent.isDebug())
                LFLogger.info("Serving XML resources index");
        } else {
            index = AssetUtils.generateTxtIndex();
            if (LegacyFixAgent.isDebug())
                LFLogger.info("Serving TXT resources index");
        }

        this.stream = new ByteArrayInputStream(index.getBytes("UTF-8"));

        return this.stream;
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                XML_INDEX_PATTERN,
                TXT_INDEX_PATTERN
        );
    }
}
