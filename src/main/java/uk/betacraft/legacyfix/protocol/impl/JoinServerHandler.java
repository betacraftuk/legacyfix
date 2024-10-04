package uk.betacraft.legacyfix.protocol.impl;

import org.json.JSONObject;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.util.Request;
import uk.betacraft.util.RequestUtil;
import uk.betacraft.util.WebData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
            LFLogger.error("JoinServerHandler", "No match for join server URL :(");
            return new ByteArrayInputStream("LF - No match for join server URL".getBytes());
        }
        String sessionId = matcher.group(8);
        String serverId = matcher.group(10);

        String uuid = System.getProperty("minecraft.user.uuid", "no-uuid");
        String accessToken;
        if (sessionId.contains(":"))
            accessToken = sessionId.split(":")[1];
        else
            accessToken = sessionId;

        WebData response = RequestUtil.performRawPOSTRequest(
                new Request()
                        .setUrl("https://sessionserver.mojang.com/session/minecraft/join")
                        .setHeader("Content-Type", "application/json")
                        .setPayload(
                                new JSONObject()
                                        .put("serverId", serverId)
                                        .put("accessToken", accessToken)
                                        .put("selectedProfile", uuid)
                        )
        );

        String write = response.getResponseCode() == 204 ? "ok" : "Invalid session (Try restarting your game)";

        return new ByteArrayInputStream(write.getBytes());
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                JOIN_SERVER_PATTERN
        );
    }
}
