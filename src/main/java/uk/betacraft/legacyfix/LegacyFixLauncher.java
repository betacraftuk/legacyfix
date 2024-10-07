package uk.betacraft.legacyfix;

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
    }

    public static String getValue(String key, String alt) {
        if (!arguments.contains("--" + key))
            return alt;

        return arguments.get(arguments.indexOf("--" + key) + 1);
    }
}
