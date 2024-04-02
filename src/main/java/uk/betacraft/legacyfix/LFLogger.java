package uk.betacraft.legacyfix;

public class LFLogger {
    public static void info(String msg) {
        System.out.println("[INFO] LF: " + msg);
    }

    public static void error(String ...lines) {
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                System.out.println("[ERROR] LF: " + lines[i]);
            } else {
                System.out.println("        " + lines[i]);
            }
        }
    }
}
