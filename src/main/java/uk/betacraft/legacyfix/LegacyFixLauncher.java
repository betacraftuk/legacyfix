package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.protocol.LegacyURLStreamHandlerFactory;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class LegacyFixLauncher {
    public static List<String> arguments = new LinkedList<String>();

    public static void main(String[] args) {
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

        URL.setURLStreamHandlerFactory(new LegacyURLStreamHandlerFactory());
        launch();
    }

    private static void launch() {
        String[] classes = {
                getValue("appletClass", "net.minecraft.client.MinecraftApplet"),
                getValue("mainClass", "net.minecraft.client.main.Main"),
                "com.mojang.minecraft.MinecraftApplet"
        };

        List<String> appletClasses = Arrays.asList(
                getValue("appletClass", "net.minecraft.client.MinecraftApplet"),
                "com.mojang.minecraft.MinecraftApplet"
        );

        StringBuilder classesNotFound = new StringBuilder();
        for (String className : classes) {
            try {
                Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass(className);

                if (appletClasses.contains(clazz.getName())) {
                    Object appletInstance = clazz.newInstance();
                    clazz.getDeclaredMethod("init").invoke(appletInstance);
                } else {
                    clazz.getMethod("main", String[].class).invoke(null, (Object) getAcceptableArguments());
                }

                return;
            } catch (ClassNotFoundException ignored) {
                if (classesNotFound.length() > 0) {
                    classesNotFound.append(", ");
                }
                classesNotFound.append(className);
            } catch (Throwable t) {
                LFLogger.error("Failed to launch Minecraft");
                LFLogger.error("launch", t);
                return;
            }
        }

        LFLogger.error("Failed to find any valid main class! Tried: " + classesNotFound);
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

        LFLogger.debug("Using args: " + args);
        return args;
    }

    public static String getValue(String key, String alt) {
        if (!hasKey(key)) {
            LFLogger.debug("Key " + key + " not found");
            return alt;
        }

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
    @SuppressWarnings("unused")
    public static int getWidth() {
        return Integer.parseInt(getValue("width", "854"));
    }

    // Used by DeAwtPatch & LWJGLFramePatch
    @SuppressWarnings("unused")
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
