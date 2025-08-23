package uk.betacraft.legacyfix.util;

import org.json.JSONObject;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.util.RequestUtil;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MinecraftAPI {
    // TODO: add support for the built-in launcher proxy, to make Minecraft able to communicate with APIs from outdated Java versions lacking necessary certs or TLSv1.2
    public static final String UUID_LOOKUP_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    public static final String PROFILE_LOOKUP_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    // name->uuid requests have a rate limit, so here's cache
    public static Map<String, String> cacheNameToUUID = new HashMap<String, String>();
    public static Map<String, SkinData> cacheUUIDToSkin = new HashMap<String, SkinData>();
    public static long rateLimitCooldown = -1L;

    public static String getUUID(String username) {
        String cachedUUID = cacheNameToUUID.get(username);

        if (cachedUUID != null) {
            return cachedUUID;
        } else {
            JSONObject obj = requestUUID(username);
            if (obj != null) {
                if (obj.isEmpty()) {
                    // no uuid assigned to that username
                    return "no-uuid";
                }

                String uuid = obj.getString("id");

                cacheNameToUUID.put(username, uuid);
                return uuid;
            }
        }

        return null;
    }

    public static JSONObject requestUUID(String name) {
        // wait for the rate limit to expire
        if (rateLimitCooldown > System.currentTimeMillis()) {
            return null;
        }

        try {
            URL uuidLookup = RequestUtil.createDirectURL(UUID_LOOKUP_URL + name);

            return new JSONObject(new String(RequestUtil.readInputStream(uuidLookup.openStream()), "UTF-8"));
        } catch (Throwable t) {
            String message = t.getMessage();

            // halt requests for 5 mins to chill
            if (message.contains("429 for URL")) {
                rateLimitCooldown = System.currentTimeMillis() + 300000;
            } else if (t instanceof FileNotFoundException && message.contains("https://api.minecraftservices.com")) {
                System.err.println("No player with name: " + name);
                return new JSONObject();
            } else {
                LFLogger.error("requestUUID", t);
            }
        }
        return null;
    }

    public static SkinData fetchSkin(String uuid) {
        if ("no-uuid".equals(uuid)) {
            return null;
        }

        try {
            URL profileLookup = RequestUtil.createDirectURL(PROFILE_LOOKUP_URL + uuid);

            JSONObject profile = new JSONObject(new String(RequestUtil.readInputStream(profileLookup.openStream()), "UTF-8"));
            String base64tex = profile.getJSONArray("properties").getJSONObject(0).getString("value");

            JSONObject textures = new JSONObject(new String(Base64Utils.decode(base64tex), "UTF-8")).getJSONObject("textures");

            JSONObject skinObj = textures.getJSONObject("SKIN");
            URL skinUrl = RequestUtil.createDirectURL(skinObj.getString("url"));

            byte[] cape;
            byte[] skin = RequestUtil.readInputStream(skinUrl.openStream());

            if (textures.has("CAPE")) {
                JSONObject capeObj = textures.getJSONObject("CAPE");
                URL capeUrl = RequestUtil.createDirectURL(capeObj.getString("url"));

                cape = RequestUtil.readInputStream(capeUrl.openStream());
            } else {
                cape = null;
            }

            boolean isAlex = skinObj.has("metadata"); // watch if this gets more features in the future

            return new SkinData(skin, cape, isAlex);
        } catch (Throwable t) {
            String message = t.getMessage();

            if (message.contains("400")) {
                LFLogger.error("Invalid uuid while fetching skin: " + uuid);
            } else {
                System.err.println("Failed to fetch skin:");
                LFLogger.error("fetchSkin", t);
            }
        }
        return null;
    }

    public static SkinData getSkin(String name) {
        String uuid = getUUID(name);
        SkinData skinData = cacheUUIDToSkin.get(uuid);

        if (skinData != null) {
            return skinData;
        }

        return fetchSkin(uuid);
    }

    // java is hella ugly
    public static class SkinData {
        public byte[] skin;
        public byte[] cape;
        public boolean alex;

        public SkinData(byte[] skin, byte[] cape, boolean alex) {
            this.skin = skin;
            this.cape = cape;
            this.alex = alex;
        }
    }
}
