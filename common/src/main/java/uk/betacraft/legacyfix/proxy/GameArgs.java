package uk.betacraft.legacyfix.proxy;

import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.assets.AssetIndexResolver;
import uk.betacraft.legacyfix.proxy.api.MinecraftApi;

import java.io.File;
import java.net.URL;

public class GameArgs {
    private static String username = null;
    private static String session = null;
    private static String uuid = null;
    private static String assetIndex = null;
    private static boolean paramsApplied = false;

    public static String getUsername() {
        return username;
    }

    public static String getSession() {
        return session;
    }

    public static String getUuid() {
        if (uuid == null) {
            uuid = MinecraftApi.getUUID(username);
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
        String index = Agent.getSetting("lf.assetIndex", GameArgs.assetIndex);
        if (isInvalidIndex(index)) {
            String ver = Agent.getSetting("lf.version", null);
            if (ver != null) {
                index = resolveIndex(ver);
            }
        }

        if (index == null) {
            Logger.error("getAssetIndexPath", "Asset index not set!");
            return null;
        }

        File indexFile = new File(getAssetsDir(), "indexes/" + index + ".json");
        try {
            AssetIndexResolver.ensureAssetIndex(index, indexFile);
        } catch (Exception e) {
            Logger.error("getAssetIndexPath", e);
        }

        return indexFile.getAbsolutePath();
    }

    public static String getScreenshotsDir() {
        return Agent.getSetting("lf.screenshotsDir", new File(getGameDir(), "screenshots").getPath());
    }

    @SuppressWarnings("unused")
    public static void setParam(String key, String value) {
        if ("username".equals(key)) {
            GameArgs.username = value;
        } else if ("sessionid".equals(key)) {
            GameArgs.session = value;
        }

        if (!GameArgs.paramsApplied && GameArgs.username != null && GameArgs.session != null) {
            setArgs(GameArgs.username, GameArgs.session);
        }
    }

    @SuppressWarnings("unused")
    public static void setArgs(String username, String session) {
        setArgsRaw(new String[]{"--username", username, "--session", session});
    }

    public static void setArgsRaw(String[] args) {
        if (GameArgs.paramsApplied || args == null || args.length == 0) {
            return;
        }

        if (!args[0].startsWith("--")) {
            GameArgs.username = args[0];
            if (args.length > 1 && !args[1].startsWith("--")) {
                GameArgs.session = args[1];
            }
        }

        for (int i = 0; i < args.length - 1; i++) {
            String key = args[i];
            String value = args[i + 1];

            if (key.startsWith("--") && value.startsWith("--")) {
                Logger.debug("setArgsRaw", "Argument " + key + " has no value, skipping");
                continue;
            }

            if ("--username".equals(key)) {
                GameArgs.username = value;
            } else if ("--uuid".equals(key)) {
                GameArgs.uuid = value;
            } else if ("--session".equals(key)) {
                GameArgs.session = value;
            } else if ("--version".equals(key)) {
                if (isInvalidVersion(value)) {
                    continue;
                }

                if (Agent.getSetting("lf.version", null) == null) {
                    Agent.setSetting("lf.version", value);
                    resolveIndex(value);
                }
            } else if ("--gameDir".equals(key)) {
                if (Agent.getSetting("lf.gameDir", null) == null) {
                    Agent.setSetting("lf.gameDir", value);
                }
            } else if ("--assetsDir".equals(key)) {
                if (isInvalidAssetsDir(value)) {
                    continue;
                }

                if (Agent.getSetting("lf.assetsDir", null) == null) {
                    Agent.setSetting("lf.assetsDir", value);
                }
            } else if ("--assetIndex".equals(key)) {
                if (isInvalidIndex(value)) {
                    continue;
                }

                if (Agent.getSetting("lf.assetIndex", null) == null) {
                    GameArgs.assetIndex = value;
                }
            }
        }

        if (!Agent.hasSetting("lf.proxy.disable")) {
            URL.setURLStreamHandlerFactory(new LegacyURLStreamHandlerFactory());
        }

        GameArgs.paramsApplied = true;
    }

    public static void setVersion(String title) {
        title = title
            .replace("Minecraft", "")
            .replace("Beta ", "b")
            .replace("Alpha ", "a")
            .replace("Infdev", "inf")
            .replace("v", "")
            .trim();

        if (title.startsWith("0.")) {
            title = "c" + title;
        }

        Logger.debug("Game version: " + title);
        resolveIndex(title);
    }

    private static String resolveIndex(String version) {
        try {
            GameArgs.assetIndex = AssetIndexResolver.resolve(version);
            AssetIndexResolver.applySettings(version);
            Logger.debug("Resolved asset index: " + GameArgs.assetIndex);
            return GameArgs.assetIndex;
        } catch (Exception e) {
            Logger.error("Failed to resolve the asset index!");
            Logger.error("resolveIndex", e);
        }

        return null;
    }

    private static boolean isInvalidVersion(String version) {
        return version == null || version.equals("Fabric");
    }

    private static boolean isInvalidIndex(String index) {
        return index == null || index.equals("legacy") || index.equals("pre-1.6");
    }

    private static boolean isInvalidAssetsDir(String dir) {
        return dir == null || (dir.contains("virtual") && dir.contains("legacy"));
    }

    public static boolean initialized() {
        return username != null || session != null;
    }
}
