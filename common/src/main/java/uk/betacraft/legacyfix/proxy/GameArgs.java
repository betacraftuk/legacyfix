package uk.betacraft.legacyfix.proxy;

import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.assets.AssetIndexResolver;
import uk.betacraft.legacyfix.proxy.api.MinecraftApi;

import java.applet.Applet;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class GameArgs {
    private static String username = null;
    private static String session = null;
    private static String uuid = null;
    private static String assetIndex = null;
    private static boolean paramsApplied = false;

    private static List<String> arguments;

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

    public static String getAssetIndexId() {
        return Agent.getSetting("lf.assetIndex", GameArgs.assetIndex);
    }

    @SuppressWarnings("unused")
    public static String getServerAddress() {
        return getValue("server", null);
    }

    @SuppressWarnings("unused")
    public static int getServerPort() {
        String portStr = getValue("port", "-1");
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Logger.error("getServerPort", "Failed to parse server port: '" + portStr + "'");
        }
        return -1;
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
        if (Agent.getSetting("lf.assetsDir", null) != null) {
            return Agent.getSetting("lf.assetsDir", null);
        }

        try {
            File potentialAssetsDir = new File(new File("").getAbsoluteFile().getParentFile().getParentFile().getParentFile(), "assets");
            File potentialObjectsDir = new File(potentialAssetsDir, "objects");
            if (potentialObjectsDir.isDirectory()) {
                String absPath = potentialAssetsDir.getAbsolutePath();
                Agent.setSetting("lf.assetsDir", absPath);

                Logger.debug("Found assets dir: " + absPath);
                return absPath;
            } else {
                Logger.error("getAssetsDir", "Couldn't find assets root directory! Please specify the path to your assets directory with the -Dlf.assetsDir argument.");
            }
        } catch (Throwable ignored) {
            if (Agent.getSetting("lf.assetsDir", null) == null) {
                Logger.error("getAssetsDir", "Couldn't get assets root directory! Please specify the path to your assets directory with the -Dlf.assetsDir argument.");
            }
        }
        return "assets";
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

        arguments = new ArrayList<String>(Arrays.asList(args));

        List<String> parsedArgs = new LinkedList<String>();
        if (args.length > 1 && !args[0].startsWith("--")) {
            parsedArgs.add("--username");
            parsedArgs.add(args[0]);

            if (!args[1].startsWith("--")) {
                parsedArgs.add("--session");
                parsedArgs.add(args[1]);
            }

            parsedArgs.addAll(Arrays.asList(args).subList(2, args.length));
        } else {
            parsedArgs = arguments;
        }

        for (int i = 0; i < parsedArgs.size() - 1; i++) {
            String key = parsedArgs.get(i);
            String value = parsedArgs.get(i + 1);

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

        // c0.30
        if (!hasKey("demo")) {
            addKey("haspaid");
        }

        if (!hasKey("mppass")) {
            setValue("mppass", "-");
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
            .replace("Infdev", "inf-(date)")
            .replace("Indev", "in-(date)")
            .replaceAll("v([0-9])", "$1")
            .trim();

        if (title.startsWith("0.")) {
            title = "c" + title;
        }

        Logger.debug("Game version: " + title);
        Agent.setSetting("lf.version", title);
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
        return dir == null || dir.contains("assets/virtual") || dir.contains("/resources");
    }

    public static boolean initialized() {
        return username != null || session != null;
    }

    public static String getValue(String key, String alt) {
        if (!hasKey(key)) {
            Logger.debug("Key " + key + " not found");
            return alt;
        }

        if (!hasValue(key)) {
            return "true";
        }

        return arguments.get(arguments.indexOf("--" + key) + 1);
    }

    public static void addKey(String key) {
        arguments.add("--" + key);
    }

    public static boolean hasKey(String key) {
        return arguments.contains("--" + key);
    }

    public static boolean hasValue(String key) {
        if (!hasKey(key)) {
            return false;
        }

        int nextIndex = arguments.indexOf("--" + key) + 1;
        if (arguments.size() <= nextIndex) {
            return false;
        }

        return !arguments.get(nextIndex).startsWith("--");
    }

    public static void setValue(String key, String value) {
        arguments.add("--" + key);
        arguments.add(value);
    }

    public static String getValueForApplet(Applet fallback, String key) {
        Logger.debug("Getting applet param value: " + key);
        // 'username' and 'sessionid' params are special because their values can be passed without keys
        if ("username".equals(key) && GameArgs.getUsername() != null) {
            return GameArgs.getUsername();
        }
        if ("sessionid".equals(key) && GameArgs.getSession() != null) {
            return GameArgs.getSession();
        }
        if ("demo".equals(key) && GameArgs.isDemo()) {
            return "true";
        }

        String value = getValue(key, null);
        if (value != null) {
            return value;
        }

        if (fallback != null && !(fallback instanceof uk.betacraft.legacyfix.applet.AppletStub) &&
                !"demo".equals(key)) { // don't ask fallback for "demo" because Prism will always provide it, even if it shouldn't
            try {
                return fallback.getParameter(key);
            } catch (Exception ignored) {}
        }

        return null;
    }

    // Used by OneSixSnapMainPatch
    @SuppressWarnings("unused")
    public static String[] getOneSixArguments() {
        if (Agent.getSetting("lf.limit13w16a", false) ||
                Agent.getSetting("lf.limit13w23a", false)) {
            List<String> args = new LinkedList<String>();

            if (hasKey("demo")) {
                args.add("--demo");
            }

            if (hasKey("fullscreen")) {
                args.add("--fullscreen");
            }

            if (hasKey("gameDir")) {
                args.add("--workDir");
                args.add(GameArgs.getGameDir());
            }

            if (hasKey("server")) {
                args.add("--server");
                args.add(getValue("server", null));
            }

            if (hasKey("port")) {
                args.add("--port");
                args.add(getValue("port", null));
            }

            if (hasKey("username")) {
                args.add("--username");
                args.add(getUsername());
            }

            if (hasKey("session")) {
                args.add("--session");
                args.add(getSession());
            }

            if (Agent.getSetting("lf.limit13w23a", false)) {
                if (hasKey("version")) {
                    args.add("--version");
                    args.add(getValue("version", "unknown"));
                }
            }
            return args.toArray(new String[0]);
        }
        return arguments.toArray(new String[0]);
    }
}
