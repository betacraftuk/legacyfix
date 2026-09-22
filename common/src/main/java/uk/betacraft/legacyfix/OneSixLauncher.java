package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.proxy.assets.AssetUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * For early 1.6 snapshots only. They can't bear additional arguments launchers give them,
 * e.g. `width` and `height`, so this filters them out.
 */
public class OneSixLauncher extends Launcher {
    private static final String[] MAIN_CLASS_CANDIDATES = {
        "net.minecraft.client.main.Main"
    };

    public static void main(String[] args) {
        GameArgs.setArgsRaw(args);

        arguments = new LinkedList<String>(Arrays.asList(args));

        AssetUtils.downloadAssets();

        launch();
    }

    private static boolean launchEntry(String className) {
        try {
            Class<?> entryClass = ClassLoader.getSystemClassLoader().loadClass(className);

            entryClass.getMethod("main", new Class[]{String[].class}).invoke(null, new Object[]{getArguments()});

            return true;
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            Logger.error("Failed launch main class! Tried \"" + className + "\"");
            Logger.error("launchEntry", t);
        }

        return false;
    }

    private static void launch() {
        String mainClassName = getValue("mainClass", null);

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

    private static String[] getArguments() {
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
