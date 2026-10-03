package uk.betacraft.legacyfix.applet;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.impl.misc.LevelProxyPatch;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.proxy.LevelProxyConfig;
import uk.betacraft.legacyfix.proxy.assets.AssetUtils;

import java.applet.Applet;

public class AppletLauncher {
    private static final String[] MAIN_CLASS_CANDIDATES = {
        "net.minecraft.client.MinecraftApplet",
        "com.mojang.minecraft.MinecraftApplet"
    };

    private static String[] rawArguments;

    public static void main(String[] args) {
        GameArgs.setArgsRaw(args);

        rawArguments = args;

        if (LevelProxyPatch.applied()) {
            LevelProxyConfig.promptIfNeeded();
        }

        AssetUtils.downloadAssets();

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
                entryClass.getMethod("main", new Class[]{String[].class}).invoke(null, new Object[]{rawArguments});
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
        String minecraftAppletClassName = GameArgs.getValue("appletClass", null);
        String mainClassName = GameArgs.getValue("mainClass", null);

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
}
