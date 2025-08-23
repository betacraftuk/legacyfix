package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.patch.impl.launch.LauncherPatch;
import uk.betacraft.legacyfix.protocol.LegacyURLStreamHandlerFactory;
import uk.betacraft.legacyfix.protocol.impl.LevelHandlerBase;
import uk.betacraft.legacyfix.util.LevelProxyAuthenticator;
import uk.betacraft.legacyfix.util.MinecraftAPI;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class LegacyFixLauncher {
    public static List<String> arguments = new LinkedList<String>();
    private static String sessionId = "-";

    private static LevelProxyAuthenticator levelProxyAuthenticator = null;

    public static void main(String[] args) {
        List<String> parsedArgs = new LinkedList<String>();

        if (args.length > 1 && !args[0].startsWith("--")) {
            parsedArgs.add("--username");
            parsedArgs.add(args[0]);

            if (!args[1].startsWith("--")) {
                parsedArgs.add("--sessionid");
                parsedArgs.add(args[1]);

                sessionId = args[1];
            }

            parsedArgs.addAll(Arrays.asList(args).subList(2, args.length));
        } else {
            parsedArgs.addAll(Arrays.asList(args));

            if (parsedArgs.contains("--sessionid")) {
                sessionId = parsedArgs.get(parsedArgs.indexOf("--sessionid") + 1);
            }
        }

        arguments = parsedArgs;

        // Classic 0.30
        if (!hasKey("demo")) {
            addKey("haspaid");
        }

        if (!hasKey("gameDir") && hasKey("workDir")) {
            setValue("gameDir", getValue("workDir", "."));
        }

        // This needs to run *after* main() initialized 'arguments'
        if (LauncherPatch.applied) {
            LauncherPatch.downloadAssetsForPrism();
        }

        URL.setURLStreamHandlerFactory(new LegacyURLStreamHandlerFactory());

        if (LevelHandlerBase.ONLINE_LEVEL_SERVER != null) {
            levelProxyAuthenticator = new LevelProxyAuthenticator();
            levelProxyAuthenticator.start();
        }

        launch();
    }

    private static boolean launchApplet(String minecraftAppletClassName) {
        try {
            Class<?> minecraftAppletClass = ClassLoader.getSystemClassLoader().loadClass(minecraftAppletClassName);
            Object minecraftApplet = minecraftAppletClass.newInstance();
            minecraftAppletClass.getDeclaredMethod("init").invoke(minecraftApplet);
            return true;
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            LFLogger.error("Failed attempt to find applet class! Tried \"" + minecraftAppletClassName + "\"");
            LFLogger.error("launchApplet", t);
        }

        return false;
    }

    private static boolean launchMain(String mainClassName) {
        try {
            Class<?> minecraftMainClass = ClassLoader.getSystemClassLoader().loadClass(mainClassName);
            minecraftMainClass.getMethod("main", new Class[]{String[].class}).invoke(null, new Object[]{getAcceptableArguments()});
            return true;
        } catch (ClassNotFoundException ignored2) {
        } catch (Throwable t) {
            LFLogger.error("Failed attempt to find the main class! Tried \"" + mainClassName + "\"");
            LFLogger.error("Failed to launch Minecraft");
            LFLogger.error("launchMain", t);
        }

        return false;
    }

    private static void launch() {
        String minecraftAppletClassName = getValue("appletClass", null);
        String mainClassName = getValue("mainClass", null);

        if (minecraftAppletClassName != null && !launchApplet(minecraftAppletClassName)) {
            LFLogger.error("Failed to find explicitly specified applet class: \"" + minecraftAppletClassName + "\"");
            return;
        }

        if (mainClassName != null && !launchMain(mainClassName)) {
            LFLogger.error("Failed to find explicitly specified main class: \"" + mainClassName + "\"");
            return;
        }

        if (!launchApplet("com.mojang.minecraft.MinecraftApplet")) {
            if (!launchApplet("net.minecraft.client.MinecraftApplet")) {
                if (!launchMain("net.minecraft.client.main.Main")) {
                    LFLogger.error("Failed to find the starting Minecraft class");
                }
            }
        }
    }

    private static String[] getAcceptableArguments() {
        List<String> args = new LinkedList<String>();

        // 13w16a to 13w23a don't allow unrecognized arguments
        if (hasKey("limit13w16a")) {
            args.addAll(limit(false));
        } else if (hasKey("limit13w23a")) {
            args.addAll(limit(true));
        } else {
            // 13w23b and later finally allow unrecognized arguments
            args.addAll(arguments);
        }

        return args.toArray(new String[0]);
    }

    private static List<String> limit(boolean is13w23a) {
        List<String> args = new LinkedList<String>();

        if (hasKey("demo")) {
            args.add("--demo");
        }

        if (hasKey("fullscreen")) {
            args.add("--fullscreen");
        }

        if (hasKey("gameDir")) {
            args.add("--workDir");
            args.add(getGameDir());
        }

        if (hasKey("server")) {
            args.add("--server");
            args.add(getValue("server", "localhost"));
        }

        if (hasKey("port")) {
            args.add("--port");
            args.add(getValue("port", "25565"));
        }

        if (hasKey("username")) {
            args.add("--username");
            args.add(getValue("username", "Player"));
        }

        if (hasKey("session")) {
            args.add("--session");
            args.add(getValue("session", "-"));
        }

        if (is13w23a) {
            if (hasKey("version")) {
                args.add("--version");
                args.add(getValue("version", "unknown"));
            }
        }

        LFLogger.debug("Using args: " + args);
        return args;
    }

    public static boolean hasValue(String key) {
        if (!hasKey(key)) {
            LFLogger.debug("Key " + key + " not found");
            return false;
        }

        int nextIndex = arguments.indexOf("--" + key) + 1;
        if (arguments.size() <= nextIndex) {
            return false;
        }

        return !arguments.get(nextIndex).startsWith("--");
    }

    public static String getValue(String key, String alt) {
        if (!hasKey(key)) {
            LFLogger.debug("Key " + key + " not found");
            return alt;
        }

        if (!hasValue(key)) {
            return "true";
        }

        if ("sessionid".equals(key) && levelProxyAuthenticator != null) {
            // wait for it to finish, otherwise it won't be possible to save online
            while (levelProxyAuthenticator.isAlive()) ;
        }

        return arguments.get(arguments.indexOf("--" + key) + 1);
    }

    public static void setValue(String key, String val) {
        if (!hasKey(key)) {
            arguments.add("--" + key);
            if (val != null) {
                arguments.add(val);
            }
        } else {
            arguments.set(arguments.indexOf("--" + key) + 1, val);
        }
    }

    public static void addKey(String key) {
        arguments.add("--" + key);
    }

    public static boolean hasKey(String key) {
        return arguments.contains("--" + key);
    }

    public static String getUUID() {
        String uuid = LegacyFixLauncher.getValue("uuid", "no-uuid");
        if (uuid.equals("no-uuid")) {
            return MinecraftAPI.getUUID(LegacyFixLauncher.getValue("username", ""));
        }

        return uuid;
    }

    public static String getSessionId() {
        return sessionId;
    }

    public static String getScreenshotsDir() {
        return getValue("screenshotsDir", new File(getGameDir(), "screenshots").getPath());
    }

    public static String getGameDir() {
        String gameDir = getValue("gameDir", null);

        if (gameDir == null) {
            try {
                return new File(".").getCanonicalPath();
            } catch (Throwable t) {
                LFLogger.error("LegacyFixLauncher", t);
                return ".";
            }
        }

        return gameDir;
    }

    public static String getAssetsDir() {
        return getValue("assetsDir", "assets");
    }

    public static String getAssetIndexPath() {
        String assetIndex = getValue("assetIndex", null);
        if (assetIndex == null) {
            return null;
        }

        return new File(getAssetsDir(), "indexes/" + assetIndex + ".json").getAbsolutePath();
    }

    // Used by DeAwtPatch & LWJGLFramePatch
    public static int getWidth() {
        return Integer.parseInt(getValue("width", "854"));
    }

    // Used by DeAwtPatch & LWJGLFramePatch
    public static int getHeight() {
        return Integer.parseInt(getValue("height", "480"));
    }

    // Used by DeAwtPatch
    @SuppressWarnings("unused")
    public static boolean getFullscreen() {
        return hasKey("fullscreen");
    }

    // Used by LWJGLFramePatch
    @SuppressWarnings("unused")
    public static String getFrameName() {
        return getValue("frameName", "Minecraft");
    }
}
