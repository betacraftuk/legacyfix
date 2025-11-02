package uk.betacraft.legacyfix.protocol.impl;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.LegacyFixLauncher;
import uk.betacraft.legacyfix.util.web.RequestUtil;
import uk.betacraft.legacyfix.util.web.WebData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class JoinServerHandler extends HandlerBase {
    private static final Pattern JOIN_SERVER_PATTERN = Pattern.compile("(http:\\/\\/((www|session)\\.minecraft\\.net)\\/(game)\\/(joinserver\\.jsp\\?user\\=)(.+)?(\\&sessionId\\=)(.+)?(\\&serverId\\=)(.+)?)");

    public JoinServerHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);
    }

    public InputStream getInputStream() throws IOException {
        Matcher matcher = JOIN_SERVER_PATTERN.matcher(this.getURLString());
        if (!matcher.find()) {
            Logger.error("JoinServerHandler", "No match for join server URL :(");
            return new ByteArrayInputStream("LF - No match for join server URL".getBytes());
        }
        String sessionId = URLDecoder.decode(matcher.group(8), "UTF-8");
        String serverId = matcher.group(10);

        WebData response = RequestUtil.performJoinServer(LegacyFixLauncher.getUUID(), sessionId, serverId);

        String write = response.getResponseCode() == 204 ? "ok" : "Invalid session (Try restarting your game)";

        return new ByteArrayInputStream(write.getBytes());
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
            JOIN_SERVER_PATTERN
        );
    }
}
