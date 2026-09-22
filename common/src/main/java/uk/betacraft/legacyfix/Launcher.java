package uk.betacraft.legacyfix;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.List;

public abstract class Launcher {
    protected static List<String> arguments;

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

    public static ImageIcon getLegacyFixIcon() {
        URL url = Launcher.class.getResource("/assets/legacyfix/icon-outlined.png");
        if (url == null) {
            return null;
        }

        Image image = new ImageIcon(url).getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }
}
