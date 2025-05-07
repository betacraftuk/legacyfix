package uk.betacraft.legacyfix.protocol.impl;

import org.json.JSONObject;
import org.json.JSONTokener;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.util.Base64Utils;
import uk.betacraft.legacyfix.util.MinecraftAPIUtils;
import uk.betacraft.legacyfix.util.SkinUtils;
import uk.betacraft.util.Request;
import uk.betacraft.util.RequestUtil;
import uk.betacraft.util.WebData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class ProfileHandler extends HandlerBase {
    private static final Pattern PROFILE_PATTERN = Pattern.compile("(https:\\/\\/sessionserver\\.mojang\\.com\\/session\\/minecraft\\/profile\\/([a-f0-9]{32})(?:\\?unsigned=(?:true|false))?)");

    public ProfileHandler(URL u, Pattern patternUsed) {
        super(u, patternUsed);

        try {
            prepare();
        } catch (Throwable t) {
            LFLogger.error("ProfileHandler", t);
        }
    }

    private void prepare() throws IOException {
        InputStream input = RequestUtil.createDirectURL(this.url.toString()).openStream();

        byte[] profileData = RequestUtil.readInputStream(input);

        this.inputStream = new ByteArrayInputStream(profileData);

        if (!SkinUtils.requiresFixing())
            return;

        Matcher matcher = this.PROFILE_PATTERN.matcher(this.getURLString());
        if (!matcher.find()) {
            LFLogger.error("ProfileHandler", "No match for skin URL :(");
            return;
        }

        String uuid = matcher.group(2);

        JSONObject response = new JSONObject(new JSONTokener(new InputStreamReader(new ByteArrayInputStream(profileData))));

        if (!response.has("properties"))
            return;

        String base64String = response.getJSONArray("properties").getJSONObject(0).getString("value");

        JSONObject texturesJson = new JSONObject(new JSONTokener(new InputStreamReader(new ByteArrayInputStream(Base64Utils.decode(base64String)))));

        if (!texturesJson.has("textures"))
            return;

        JSONObject skinJson = texturesJson.getJSONObject("textures").getJSONObject("SKIN");
        boolean alex = skinJson.has("metadata") && skinJson.getJSONObject("metadata").getString("model").equals("slim");
        String skinUrl = skinJson.getString("url");

        Request req = new Request();
        req.setUrl(skinUrl);
        WebData data = RequestUtil.performRawGETRequest(req);

        MinecraftAPIUtils.SkinData skinData = new MinecraftAPIUtils.SkinData(data.getData(), null, alex);

        byte[] fixed = SkinUtils.getFixedSkin(skinData);

        SkinTextureHandler.addSkin(skinUrl, fixed);
    }

    public static List<Pattern> regexPatterns() {
        return Arrays.asList(
                PROFILE_PATTERN
        );
    }
}
