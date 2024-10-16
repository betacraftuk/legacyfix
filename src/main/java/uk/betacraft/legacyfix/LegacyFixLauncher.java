package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.protocol.LegacyURLStreamHandlerFactory;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

public class LegacyFixLauncher {
    public static List<String> arguments = new LinkedList<String>();

    public static void main(String[] args) {
        for (String arg : args) {
            if (arguments.contains(arg) && arg.startsWith("--")) {
                LFLogger.error("LegacyFixLauncher", "Duplicate argument '" + arg + "'!");
            }

            arguments.add(arg);
        }

        URL.setURLStreamHandlerFactory(new LegacyURLStreamHandlerFactory());

        launch();
    }

    private static void launch() {
        String minecraftAppletClassName = getValue("appletClass", "net.minecraft.client.MinecraftApplet");
        try {
            Class<?> minecraftAppletClass = ClassLoader.getSystemClassLoader().loadClass(minecraftAppletClassName);
            Object minecraftApplet = minecraftAppletClass.newInstance();
            minecraftAppletClass.getDeclaredMethod("init").invoke(minecraftApplet);
        } catch (ClassNotFoundException ignored) {
            String mainClassName = getValue("mainClass", "net.minecraft.client.main.Main");
            try {
                Class<?> minecraftMainClass = ClassLoader.getSystemClassLoader().loadClass(mainClassName);
                minecraftMainClass.getMethod("main", new Class[]{String[].class}).invoke(null, new Object[]{getAcceptableArguments()});
            } catch (ClassNotFoundException ignored2) {
                LFLogger.error("Failed to find the main class! Tried \"" + minecraftAppletClassName + "\" and \"" + mainClassName + "\"");
            } catch (Throwable t) {
                LFLogger.error("Failed to launch Minecraft");
                LFLogger.error("launch", t);
            }
        } catch (Throwable t) {
            LFLogger.error("Failed to launch Minecraft");
            LFLogger.error("launch", t);
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

        if (hasKey("demo"))
            args.add("--demo");

        if (hasKey("fullscreen"))
            args.add("--fullscreen");

        if (hasKey("gameDir")) {
            args.add("--workDir");
            args.add(getValue("gameDir", "."));
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

        return args;
    }

    public static String getValue(String key, String alt) {
        if (!hasKey(key))
            return alt;

        return arguments.get(arguments.indexOf("--" + key) + 1);
    }

    public static boolean hasKey(String key) {
        return arguments.contains("--" + key);
    }

    public static String getScreenshotsDir() {
        return getValue("screenshotsDir", new File(getGameDir(), "screenshots").getPath());
    }

    public static String getGameDir() {
        return getValue("gameDir", "minecraft");
    }

    public static String getAssetsDir() {
        return getValue("assetsDir", "assets");
    }

    public static String getAssetIndexPath() {
        String assetIndex = getValue("assetIndex", null);
        if (assetIndex == null)
            return null;

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
    public static boolean getFullscreen() {
        return hasKey("fullscreen");
    }

    // Used by LWJGLFramePatch
    public static String getFrameName() {
        return getValue("frameName", "Minecraft");
    }
}
