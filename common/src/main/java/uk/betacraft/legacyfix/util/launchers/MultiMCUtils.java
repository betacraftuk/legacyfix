package uk.betacraft.legacyfix.util.launchers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.proxy.assets.AssetIndexResolver;
import uk.betacraft.legacyfix.util.OSUtils;

import java.io.*;
import java.util.Iterator;

public class MultiMCUtils {
    protected static final String LWJGL_LEGACYFABRIC_VERSION = "2.9.4+legacyfabric.18";

    public static boolean detect() {
        return System.getProperty("sun.java.command") != null && System.getProperty("sun.java.command").contains("org.multimc.EntryPoint");
    }

    public static boolean setup() throws Exception {
        MMCVersionInfo mmcInfo = readMMCVersion();
        if (mmcInfo == null) {
            throw new Exception("Failed to read MMC version info");
        }

        return patchMinecraftJson(mmcInfo);
    }

    protected static MMCVersionInfo readMMCVersion() {
        File mmcPackJsonFile = new File("../mmc-pack.json");
        if (!mmcPackJsonFile.exists()) {
            Logger.error("readMMCPackJson", "Could not find mmc-pack.json of this instance, can't download assets");
            return null;
        }

        JSONObject mmcPackJson;
        try {
            mmcPackJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(mmcPackJsonFile))));
        } catch (FileNotFoundException e) {
            Logger.error("readMMCPackJson", e);
            return null;
        }

        return new MMCVersionInfo(mmcPackJson);
    }

    protected static boolean patchMinecraftJson(MMCVersionInfo mmcVersionInfo) {
        // don't mess with loaders.
        boolean hasLoader = mmcVersionInfo.forgeVersion != null || mmcVersionInfo.fabricVersion != null;

        if (Agent.hasSetting("lf.keep-net.minecraft.json")) {
            return false;
        }

        File netMinecraftJsonFile = new File("../patches/net.minecraft.json");
        JSONObject netMinecraftJson = readMMCJson(netMinecraftJsonFile, new File("../../../meta/net.minecraft/" + mmcVersionInfo.minecraftVersion + ".json"));
        if (netMinecraftJson == null) {
            return false;
        }

        boolean changed = false;

        if (netMinecraftJson.has("+traits")) {
            JSONArray traits = netMinecraftJson.getJSONArray("+traits");
            Iterator<Object> it = traits.iterator();
            while (it.hasNext()) {
                Object element = it.next();
                if (!(element instanceof String)) {
                    continue;
                }
                String trait = (String) element;
                if (trait.equals("legacyServices") || (!hasLoader && trait.equals("legacyLaunch"))) {
                    it.remove();
                    changed = true;
                }
            }
        }

        if (!hasLoader && netMinecraftJson.has("appletClass")) {
            netMinecraftJson.remove("appletClass");
            changed = true;
        }

        boolean usesApplet = Agent.getSetting("lf.applet", false);

        if (!hasLoader && usesApplet && !netMinecraftJson.optString("mainClass").equals("uk.betacraft.legacyfix.applet.AppletLauncher")) {
            netMinecraftJson.put("mainClass", "uk.betacraft.legacyfix.applet.AppletLauncher");
            changed = true;
        }

        String minecraftArgs = "--username ${auth_player_name} --session ${auth_session} --accessToken ${auth_access_token} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --version ${version_name} --userProperties ${user_properties} --userType ${user_type} --versionType ${version_type}";
        if (!netMinecraftJson.optString("minecraftArguments").equals(minecraftArgs)) {
            netMinecraftJson.put("minecraftArguments", minecraftArgs);
            changed = true;
        }

        replaceAssetIndex: {
            String assetIndexId = GameArgs.getAssetIndexId();
            JSONObject assetIndexJson = AssetIndexResolver.getAssetIndexSnippetJson(assetIndexId);
            if (assetIndexJson == null) {
                Logger.error("patchMinecraftJson", "Could not find asset index snippet for this asset index id '" + assetIndexId +  "', can't replace asset index in mmc Minecraft component");
                break replaceAssetIndex;
            }

            if (!assetIndexJson.optString("sha1").equals(netMinecraftJson.optJSONObject("assetIndex").optString("sha1"))) {
                // MultiMC on Windows XP is unable to download from our server via HTTPS, refusing to launch (*sigh*)
                if (OSUtils.getOSName().equals("windows xp") || OSUtils.isVeryOldWindows()) {
                    String httpUrl = assetIndexJson.getString("url").replace("https://", "http://");
                    assetIndexJson.put("url", httpUrl);
                }

                netMinecraftJson.put("assetIndex", assetIndexJson);
                changed = true;
            }
        }

        replaceLwjglRequires: {
            if (!PrismLauncherUtils.shouldPatchLwjgl(mmcVersionInfo)) {
                break replaceLwjglRequires;
            }

            JSONArray requires = netMinecraftJson.optJSONArray("requires");
            if (requires == null) {
                break replaceLwjglRequires;
            }

            for (int i = 0; i < requires.length(); i++) {
                JSONObject require = requires.getJSONObject(i);
                if (!require.optString("uid").equals("org.lwjgl")) {
                    continue;
                }

                if (require.optString("suggests").equals(LWJGL_LEGACYFABRIC_VERSION)) {
                    continue;
                }

                require.put("suggests", LWJGL_LEGACYFABRIC_VERSION);

                requires.remove(i);
                requires.put(i, require);

                netMinecraftJson.put("requires", requires);
                changed = true;
                break;
            }
        }

        if (changed) {
            saveMMCJson(netMinecraftJsonFile, netMinecraftJson);

            Logger.debug("patchMinecraftJson", "Patched 'net.minecraft.json'");
        } else {
            Logger.debug("patchMinecraftJson", "'net.minecraft.json' has been already patched");
        }

        return changed;
    }

    protected static JSONObject readMMCJson(File mmcJsonFile, File srcMMCJsonFile) {
        JSONObject mmcJson;
        if (!mmcJsonFile.exists()) {
            try {
                mmcJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(srcMMCJsonFile))));
            } catch (FileNotFoundException e) {
                Logger.error("readMMCJson", e);
                return null;
            }
        } else {
            try {
                mmcJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(mmcJsonFile))));
            } catch (FileNotFoundException e) {
                Logger.error("readMMCJson", e);
                return null;
            }
        }

        return mmcJson;
    }

    protected static void saveMMCJson(File mmcJsonFile, JSONObject mmcJson) {
        try {
            mmcJsonFile.getParentFile().mkdirs();

            FileOutputStream fos = new FileOutputStream(mmcJsonFile);
            fos.write(mmcJson.toString(4).getBytes("UTF-8"));
            fos.close();
        } catch (Throwable t) {
            Logger.error("saveMMCJson", "Failed to save MMC json to: " + mmcJsonFile.getAbsolutePath());
            Logger.error("saveMMCJson", t);
        }
    }

    public static class MMCVersionInfo {
        public String minecraftVersion;
        public String lwjglVersion;
        public String lwjglCachedVersion;
        public String forgeVersion;
        public String fabricVersion;
        private JSONObject mmcPackJson;

        public MMCVersionInfo(String minecraftVersion, String lwjglVersion, String forgeVersion, String fabricVersion) {
            this.minecraftVersion = minecraftVersion;
            this.lwjglVersion = lwjglVersion;
            this.forgeVersion = forgeVersion;
            this.fabricVersion = fabricVersion;
        }

        public MMCVersionInfo(JSONObject mmcPackJson) {
            this.mmcPackJson =  mmcPackJson;

            JSONArray componentsArray = mmcPackJson.optJSONArray("components", new JSONArray());
            for (int i = 0; i < componentsArray.length(); i++) {
                JSONObject component = componentsArray.getJSONObject(i);
                String uid = component.getString("uid");

                if ("net.minecraft".equals(uid)) {
                    this.minecraftVersion = component.getString("version");
                } else if ("org.lwjgl".equals(uid)) {
                    this.lwjglVersion = component.getString("version");
                    this.lwjglCachedVersion = component.optString("cachedVersion");
                } else if ("net.minecraftforge".equals(uid)) {
                    this.forgeVersion = component.getString("version");
                } else if ("net.fabricmc.fabric-loader".equals(uid)) {
                    this.fabricVersion = component.getString("version");
                }
            }
        }

        public JSONObject getMmcPackJson() {
            return this.mmcPackJson;
        }
    }
}