package uk.betacraft.legacyfix.proxy;

import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.assets.AssetIndexResolver;
import uk.betacraft.legacyfix.util.MinecraftAPI;

import java.io.File;
import java.net.URL;

public class GameArgs {
    private static String username = null;
    private static String session = null;
    private static String uuid = null;
    private static String assetIndex = null;

    public static String getUsername() {
        return username;
    }

    public static String getSession() {
        return session;
    }

    public static String getUuid() {
        if (uuid == null) {
            uuid = MinecraftAPI.getUUID(username);
        }

        return uuid;
    }

    public static boolean isDemo() {
        return Agent.hasSetting("lf.demo");
    }

    public static String getGameDir() {
        String gameDir = Agent.getSetting("lf.gameDir", null);

        if (gameDir == null) {
            try {
                return new File(".").getCanonicalPath();
            } catch (Throwable t) {
                Logger.error("GameArgs", t);
                return ".";
            }
        }

        return gameDir;
    }

    public static String getAssetsDir() {
        return Agent.getSetting("lf.assetsDir", "assets");
    }

    public static String getAssetIndexPath() {
        String assetIndex = Agent.getSetting("lf.assetIndex", GameArgs.assetIndex);
        if (assetIndex == null) {
            Logger.error("getAssetIndexPath", "Asset index not set!");
            return null;
        }

        File indexFile = new File(getAssetsDir(), "indexes/" + assetIndex + ".json");
        try {
            AssetIndexResolver.ensureAssetIndex(assetIndex, indexFile);
        } catch (Exception e) {
            Logger.error("getAssetIndexPath", e);
        }

        return indexFile.getAbsolutePath();
    }

    public static String getScreenshotsDir() {
        return Agent.getSetting("lf.screenshotsDir", new File(getGameDir(), "screenshots").getPath());
    }

    public static void setArgs(String username, String session) {
        GameArgs.username = username;
        GameArgs.session = session;

        if (Agent.hasSetting("lf.proxy.disable")) {
            return;
        }

        URL.setURLStreamHandlerFactory(new LegacyURLStreamHandlerFactory());
    }

    public static void setVersion(String title) {
        title = title
            .replace("Minecraft", "")
            .replace("Beta ", "b")
            .replace("Alpha ", "a")
            .replace("v", "")
            .trim();

        if (title.startsWith("0.")) {
            title = "c" + title;
        }

        Logger.debug("Game version: " + title);

        try {
            assetIndex = AssetIndexResolver.resolve(title);
            Logger.debug("Resolved asset index: " + assetIndex);
        } catch (Exception e) {
            Logger.error("Failed to resolve the asset index!");
        }
    }

    public static boolean initialized() {
        return username != null || session != null;
    }
}
