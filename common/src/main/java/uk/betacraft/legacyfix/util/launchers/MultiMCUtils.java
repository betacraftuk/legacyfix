package uk.betacraft.legacyfix.util.launchers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.proxy.assets.AssetIndexResolver;
import uk.betacraft.legacyfix.util.FileUtils;
import uk.betacraft.legacyfix.util.OSUtils;
import uk.betacraft.legacyfix.util.web.RequestUtil;

import java.io.*;
import java.util.Iterator;

public class MultiMCUtils {
    public static boolean detect() {
        return System.getProperty("sun.java.command") != null && System.getProperty("sun.java.command").contains("org.multimc.EntryPoint");
    }

    public static boolean setup() throws Exception {
        fetch1_3SnapshotsServer();

        removeResourcesAndAssets();

        MMCVersionInfo mmcInfo = readMMCVersion();
        if (mmcInfo == null) {
            throw new Exception("Failed to read MMC version info");
        }

        findAssetsDir(mmcInfo);

        return patchMinecraftJson(mmcInfo);
    }

    protected static void findAssetsDir(MMCVersionInfo mmcVersionInfo) {
        if (Agent.active || mmcVersionInfo.forgeVersion == null) {
            // Rely on other, better methods when not running as a Forge mod.
            return;
        }

        // Try finding ${assets_root} because we will never get it from Prism/MMC if we're running Forge.
        try {
            if (Agent.getSetting("lf.assetsDir", null) == null) {
                File potentialAssetsDir = new File("../../../assets");
                if (new File(potentialAssetsDir, "objects").exists()) {
                    Agent.setSetting("lf.assetsDir", potentialAssetsDir.getCanonicalPath());
                } else {
                    Logger.error("Couldn't find assets root directory! Please specify the path to your assets directory with the -Dlf.assetsDir argument.");
                }
            }
        } catch (Throwable ignored) {
            if (Agent.getSetting("lf.assetsDir", null) == null) {
                Logger.error("Couldn't find assets root directory! Please specify the path to your assets directory with the -Dlf.assetsDir argument.");
            }
        }
    }

    protected static MMCVersionInfo readMMCVersion() {
        File mmcPackJsonFile = new File("../mmc-pack.json");
        if (!mmcPackJsonFile.exists()) {
            Logger.error("Could not find mmc-pack.json of this instance, can't download assets");
            return null;
        }

        JSONObject mmcPackJson;
        try {
            mmcPackJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(mmcPackJsonFile))));
        } catch (FileNotFoundException e) {
            Logger.error("MultiMCUtils", e);
            return null;
        }

        String baseVersion = null;
        String lwjglVersion = null;
        String forgeVersion = null;

        JSONArray componentsArray = mmcPackJson.getJSONArray("components");
        for (int i = 0; i < componentsArray.length(); i++) {
            JSONObject component = componentsArray.getJSONObject(i);
            String uid = component.getString("uid");

            if ("net.minecraft".equals(uid)) {
                baseVersion = component.getString("version");
            } else if ("org.lwjgl".equals(uid)) {
                lwjglVersion = component.getString("version");
            } else if ("net.minecraftforge".equals(uid)) {
                forgeVersion = component.getString("version");
            }
        }

        return new MMCVersionInfo(baseVersion, lwjglVersion, forgeVersion);
    }

    protected static boolean patchMinecraftJson(MMCVersionInfo mmcVersionInfo) {
        // don't mess with Forge.
        boolean hasForge = mmcVersionInfo.forgeVersion != null;

        if (Agent.hasSetting("lf.keep-net.minecraft.json")) {
            return false;
        }

        File netMinecraftJsonFile = new File("../patches/net.minecraft.json");
        JSONObject netMinecraftJson = readMMCJson(netMinecraftJsonFile, new File("../../../meta/net.minecraft/" + mmcVersionInfo.minecraftVersion + ".json"));
        if (netMinecraftJson == null) {
            return false;
        }

        boolean changed = false;

        if (!hasForge && netMinecraftJson.has("+traits")) {
            JSONArray traits = netMinecraftJson.getJSONArray("+traits");
            Iterator<Object> it = traits.iterator();
            while (it.hasNext()) {
                Object element = it.next();
                if (!(element instanceof String)) {
                    continue;
                }
                String trait = (String) element;
                if (trait.equals("legacyServices") || trait.equals("legacyLaunch")) {
                    it.remove();
                    changed = true;
                }
            }
        }

        if (!hasForge && netMinecraftJson.has("appletClass")) {
            netMinecraftJson.remove("appletClass");
            changed = true;
        }

        boolean usesApplet = Agent.getSetting("lf.applet", false);
        boolean usesOneSix = Agent.getSetting("lf.onesix", false);

        if (!hasForge && usesApplet && !netMinecraftJson.optString("mainClass").equals("uk.betacraft.legacyfix.applet.AppletLauncher")) {
            netMinecraftJson.put("mainClass", "uk.betacraft.legacyfix.applet.AppletLauncher");
            changed = true;
        }

        if (!hasForge && usesOneSix && !netMinecraftJson.optString("mainClass").equals("uk.betacraft.legacyfix.OneSixLauncher")) {
            netMinecraftJson.put("mainClass", "uk.betacraft.legacyfix.OneSixLauncher");
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
                Logger.error("Could not find asset index snippet for this asset index id '" + assetIndexId +  "', can't replace asset index in mmc Minecraft component");
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

        if (changed) {
            saveMMCJson(netMinecraftJsonFile, netMinecraftJson);

            Logger.debug("Patched 'net.minecraft.json'");
        } else {
            Logger.debug("'net.minecraft.json' has been already patched");
        }

        return changed;
    }

    protected static boolean patchLwjglJson(MMCVersionInfo mmcVersionInfo) {
        return false; // no for MultiMC
    }

    protected static void removeResourcesAndAssets() {
        File resourcesDir = new File("resources");
        if (resourcesDir.exists() && !Agent.hasSetting("lf.keep-resources")) {
            FileUtils.removeRecursively(resourcesDir, false, false);
        }

        File assetsDir = new File("assets");
        if (new File(assetsDir, "objects").exists()) { // don't accidentally delete ${assets_root}! O.O
            return;
        }
        if (assetsDir.exists() && !Agent.hasSetting("lf.keep-assets")) {
            FileUtils.removeRecursively(assetsDir, false, false);
        }
    }

    protected static void fetch1_3SnapshotsServer() throws Exception {
        String minecraftVersion = Agent.getSetting("lf.version", null);
        if (minecraftVersion == null || !minecraftVersion.startsWith("12w18a") && !minecraftVersion.startsWith("12w19a") && !minecraftVersion.startsWith("12w21a")) {
            return;
        }

        String actualVersion = minecraftVersion.substring(0, 6);

        File serverJarFile = new File("server/minecraft_server.jar");
        if (serverJarFile.exists()) {
            return;
        }

        serverJarFile.getParentFile().mkdirs();

        String url = "https://vault.omniarchive.uk/archive/java/server-release/1.3/pre/" + actualVersion + ".jar";

        Logger.info("Downloading server for: '" + actualVersion + "'");
        if (!RequestUtil.download(url, serverJarFile)) {
            Logger.error("launcher", "Failed to download server for '" + actualVersion + "'");
            throw new Exception("Failed to download server for '" + actualVersion + "'");
        }
    }

    protected static JSONObject readMMCJson(File mmcJsonFile, File srcMMCJsonFile) {
        JSONObject mmcJson;
        if (!mmcJsonFile.exists()) {
            try {
                mmcJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(srcMMCJsonFile))));
            } catch (FileNotFoundException e) {
                Logger.error("Could not read MMC json", e);
                return null;
            }
        } else {
            try {
                mmcJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(mmcJsonFile))));
            } catch (FileNotFoundException e) {
                Logger.error("Could not read MMC json", e);
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
            Logger.error("launcher", "Failed to save MMC json to: " + mmcJsonFile.getAbsolutePath());
            Logger.error("launcher", t);
        }
    }

    public static class MMCVersionInfo {
        public String minecraftVersion;
        public String lwjglVersion;
        public String forgeVersion;

        public MMCVersionInfo(String minecraftVersion, String lwjglVersion, String forgeVersion) {
            this.minecraftVersion = minecraftVersion;
            this.lwjglVersion = lwjglVersion;
            this.forgeVersion = forgeVersion;
        }
    }
}
