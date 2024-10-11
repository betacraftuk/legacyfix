package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.protocol.LegacyURLStreamHandlerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;

public class LegacyFixLauncher {
    public static List<String> arguments = new LinkedList<String>();

    public static void main(String[] args) {
        for (String arg : args) {
            if (arguments.contains(arg)) {
                LFLogger.error("LegacyFixLauncher", "Duplicate argument '" + arg + "'!");
            }

            arguments.add(arg);
        }

        String minecraftAppletClassName = getValue("appletClass", "net.minecraft.client.MinecraftApplet");

        try {
            Class<?> minecraftAppletClass = ClassLoader.getSystemClassLoader().loadClass(minecraftAppletClassName);
            Object minecraftApplet = minecraftAppletClass.newInstance();
            minecraftAppletClass.getDeclaredMethod("init").invoke(minecraftApplet);
        } catch (Throwable t) {
            LFLogger.error("Failed to launch Minecraft");
            LFLogger.error("LegacyFixLauncher", t);
        }

        URL.setURLStreamHandlerFactory(new LegacyURLStreamHandlerFactory());
    }

    public static String getValue(String key, String alt) {
        if (!hasKey(key))
            return alt;

        return arguments.get(arguments.indexOf("--" + key) + 1);
    }

    public static boolean hasKey(String key) {
        return arguments.contains("--" + key);
    }

    public static String getGameDir() {
        return LegacyFixLauncher.getValue("gameDir", "minecraft");
    }

    public static String getAssetsDir() {
        return LegacyFixLauncher.getValue("assetsDir", "assets");
    }

    public static String getAssetIndexPath() {
        String assetIndex = LegacyFixLauncher.getValue("assetIndex", "empty");

        return new File(getAssetsDir(), "indexes/" + assetIndex + ".json").getAbsolutePath();
    }

    // Used by DeAwtPatch & LWJGLFramePatch
    public static int getWidth() {
        return Integer.parseInt(LegacyFixLauncher.getValue("width", "854"));
    }

    // Used by DeAwtPatch & LWJGLFramePatch
    public static int getHeight() {
        return Integer.parseInt(LegacyFixLauncher.getValue("height", "480"));
    }

    // Used by DeAwtPatch
    public static boolean getFullscreen() {
        return Boolean.parseBoolean(LegacyFixLauncher.getValue("fullscreen", "false"));
    }

    // Used by LWJGLFramePatch
    public static String getFrameName() {
        return LegacyFixLauncher.getValue("version", "Minecraft");
    }
}
