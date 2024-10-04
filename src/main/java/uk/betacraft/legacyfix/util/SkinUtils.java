package uk.betacraft.legacyfix.util;

import org.json.JSONObject;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.util.RequestUtil;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class SkinUtils {
    // TODO: add support for the built-in launcher proxy, to make Minecraft able to communicate with APIs from outdated Java versions lacking necessary certs or TLSv1.2
    public static final String UUID_LOOKUP_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    public static final String PROFILE_LOOKUP_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    // name->uuid requests have a rate limit, so here's cache
    public static Map<String, String> cacheNameToUUID = new HashMap<String, String>();
    public static Map<String, SkinData> cacheUUIDToSkin = new HashMap<String, SkinData>();
    public static long rateLimitCooldown = -1L;

    public static final boolean OVERLAY_OUTER_HEAD_LAYER = System.getProperties().containsKey("lf.OVERLAY_OUTER_HEAD_LAYER");
    public static final boolean OVERLAY_OUTER_BODY_TO_BASE = System.getProperties().containsKey("lf.OVERLAY_OUTER_BODY_TO_BASE");
    public static final boolean ROTATE_BOTTOM_TEXTURES = System.getProperties().containsKey("lf.ROTATE_BOTTOM_TEXTURES");
    public static final boolean CONVERT_ALEX_TO_STEVE = System.getProperties().containsKey("lf.CONVERT_ALEX_TO_STEVE");
    public static final boolean SERVE_AS_64x32 = System.getProperties().containsKey("lf.SERVE_AS_64x32");

    public static String getUUID(String username) {
        String cachedUUID = cacheNameToUUID.get(username);

        if (cachedUUID != null) {
            return cachedUUID;
        } else {
            JSONObject obj = requestUUID(username);
            if (obj != null) {
                if (obj.isEmpty()) // no uuid assigned to that username
                    return "no-uuid";

                String uuid = obj.getString("id");

                cacheNameToUUID.put(username, uuid);
                return uuid;
            }
        }

        return null;
    }

    public static JSONObject requestUUID(String name) {
        // wait for the rate limit to expire
        if (rateLimitCooldown > System.currentTimeMillis())
            return null;

        try {
            URL uuidLookup = new URI(UUID_LOOKUP_URL + name).toURL();

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
        try {
            URL profileLookup = new URI(PROFILE_LOOKUP_URL + uuid).toURL();

            JSONObject profile = new JSONObject(new String(RequestUtil.readInputStream(profileLookup.openStream()), "UTF-8"));
            String base64tex = profile.getJSONArray("properties").getJSONObject(0).getString("value");

            JSONObject textures = new JSONObject(new String(Base64Utils.decode(base64tex), "UTF-8")).getJSONObject("textures");

            JSONObject skinObj = textures.getJSONObject("SKIN");
            URL skinUrl = new URI(skinObj.getString("url")).toURL();

            byte[] cape;
            byte[] skin = RequestUtil.readInputStream(skinUrl.openStream());

            JSONObject capeObj = textures.getJSONObject("CAPE");
            if (capeObj != null) {
                URL capeUrl = new URL(capeObj.getString("url"));

                cape = RequestUtil.readInputStream(capeUrl.openStream());
            } else {
                cape = null;
            }

            boolean isAlex = skinObj.has("metadata"); // watch if this gets more features in the future

            return new SkinData(skin, cape, isAlex);
        } catch (Throwable t) {
            String message = t.getMessage();

            if (message.contains("400")) {
                System.err.print("Invalid uuid while fetching skin: " + uuid);
            } else {
                System.err.println("Failed to fetch skin:");
                LFLogger.error("fetchSkin", t);
            }
        }
        return null;
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

    public static SkinData getSkin(String name) {
        String uuid = getUUID(name);
        SkinData skinData = cacheUUIDToSkin.get(uuid);

        if (skinData != null) {
            return skinData;
        }

        return fetchSkin(uuid);
    }

    public static byte[] getFixedCape(SkinData skinData) {
        try {
            if (skinData.cape != null) {
                ByteArrayInputStream bis = new ByteArrayInputStream(skinData.cape);

                ImageUtils img = new ImageUtils(bis);
                return img.crop(0, 0, 64, 32).getInByteForm();
            }
        } catch (Throwable t) {
            LFLogger.error("getFixedCape", t);
        }
        return null;
    }

    public static byte[] getFixedSkin(SkinData skinData) {
        try {
            if (skinData.skin != null) {
                ByteArrayInputStream bis = new ByteArrayInputStream(skinData.skin);
                ImageUtils img = new ImageUtils(bis);

                // classic before 0.24
                if (OVERLAY_OUTER_HEAD_LAYER)
                    img = overlayHeadLayer(img);

                // before 1.8
                if (OVERLAY_OUTER_BODY_TO_BASE && img.getImage().getHeight() == 64)
                    img = overlay64to32(img);

                // before 1.8
                if (CONVERT_ALEX_TO_STEVE && skinData.alex)
                    alexToSteve(img);

                // before b1.9-pre1
                if (ROTATE_BOTTOM_TEXTURES)
                    rotateBottomTX(img);

                // before 1.8
                if (SERVE_AS_64x32)
                    img = img.crop(0, 0, 64, 32);

                return img.getInByteForm();
            }
        } catch (Throwable t) {
            LFLogger.error("getFixedSkin", t);
        }
        return null;
    }

    public static void rotateBottomTX(ImageUtils img) {
        // bottom head
        img.setArea(16, 0, img.crop(16, 0, 8, 8).flip(false, true).getImage());
        // bottom head layer
        img.setArea(48, 0, img.crop(48, 0, 8, 8).flip(false, true).getImage());

        // bottom feet
        img.setArea(8, 16, img.crop(8, 16, 4, 4).flip(false, true).getImage());
        // bottom hand
        img.setArea(48, 16, img.crop(48, 16, 4, 4).flip(false, true).getImage());
        // bottom torso
        img.setArea(28, 16, img.crop(28, 16, 8, 4).flip(false, true).getImage());
    }

    public static ImageUtils overlay64to32(ImageUtils img) {
        // 32-64 body
        return img.setArea(0, 16, img.crop(0, 32, 56, 16).getImage(), false);
    }

    public static ImageUtils overlayHeadLayer(ImageUtils img) {
        return img.setArea(0, 0, img.crop(32, 0, 32, 16).getImage(), false);
    }

    public static void alexToSteve(ImageUtils img) {
        // make space for the left arm 1px texture
        img.setArea(48, 20, img.crop(47, 20, 7, 12).getImage());
        // fill the 1px space in between
        img.setArea(47, 20, img.crop(46, 20, 1, 12).getImage());
        // fill the 1px space on the right
        img.setArea(55, 20, img.crop(54, 20, 1, 12).getImage());

        // bottom hand
        img.setArea(48, 16, img.crop(47, 16, 3, 4).getImage());
        // fill the 1px of the shoulder
        img.setArea(47, 16, img.crop(46, 16, 1, 4).getImage());
        // fill the 1px space on the right
        img.setArea(51, 16, img.crop(50, 16, 1, 4).getImage());
    }
}