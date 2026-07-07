package uk.betacraft.legacyfix.applet;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.impl.misc.LevelProxyPatch;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.proxy.LevelProxyConfig;

import java.applet.Applet;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class AppletLauncher {
    private static final String[] MAIN_CLASS_CANDIDATES = {
        "net.minecraft.client.MinecraftApplet",
        "com.mojang.minecraft.MinecraftApplet"
    };

    private static List<String> arguments;

    public static void main(String[] args) {
        GameArgs.setArgsRaw(args);

        List<String> parsedArgs = new LinkedList<String>();
        if (args.length > 1 && !args[0].startsWith("--")) {
            parsedArgs.add("--username");
            parsedArgs.add(args[0]);

            if (!args[1].startsWith("--")) {
                parsedArgs.add("--sessionid");
                parsedArgs.add(args[1]);
            }

            parsedArgs.addAll(Arrays.asList(args).subList(2, args.length));
        } else {
            parsedArgs.addAll(Arrays.asList(args));
        }

        arguments = parsedArgs;

        // c0.30
        if (!hasKey("demo")) {
            addKey("haspaid");
        }

        if (!hasKey("mppass")) {
            addKey("mppass");
            arguments.add("-");
        }

        if (LevelProxyPatch.applied()) {
            LevelProxyConfig.promptIfNeeded();
        }

        launch();
    }

    private static boolean launchEntry(String className) {
        try {
            Class<?> entryClass = ClassLoader.getSystemClassLoader().loadClass(className);
            Class<?> superClass = entryClass.getSuperclass();

            Object instance = entryClass.newInstance();
            if (superClass != null && "java.applet.Applet".equals(superClass.getName())) {
                new AppletFrame("Minecraft", (Applet) instance).launch();
            } else {
                entryClass.getMethod("main", new Class[]{String[].class}).invoke(null, new Object[]{arguments.toArray(new String[0])});
            }

            return true;
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            Logger.error("Failed launch main class! Tried \"" + className + "\"");
            Logger.error("launchEntry", t);
        }

        return false;
    }

    private static void launch() {
        String minecraftAppletClassName = getValue("appletClass", null);
        String mainClassName = getValue("mainClass", null);

        if (minecraftAppletClassName != null && !launchEntry(minecraftAppletClassName)) {
            Logger.error("Failed to find explicit applet class: \"" + minecraftAppletClassName + "\"");
            return;
        }

        if (mainClassName != null && !launchEntry(mainClassName)) {
            Logger.error("Failed to find explicit main class: \"" + mainClassName + "\"");
            return;
        }

        for (String candidate : MAIN_CLASS_CANDIDATES) {
            boolean success = launchEntry(candidate);
            Logger.debug("Main: " + candidate + " - " + success);
            if (success) {
                return;
            }
        }

        Logger.error("Failed to find the main Minecraft class");
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
}
