package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.patch.Patch;

public class LFLogger {
    public static void info(String ...lines) {
        log("INFO", lines);
    }

    public static void error(Patch patch, Exception e) {
        error(patch.getName(), e.toString());
    }

    public static void error(String ...lines) {
        log("ERROR", lines);
    }

    public static void log(String prefix, String ...lines) {
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                System.out.println("[LF] " + prefix + ": " + lines[i]);
            } else {
                System.out.println("        " + lines[i]);
            }
        }
    }
}
