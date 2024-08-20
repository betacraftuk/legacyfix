package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.patch.Patch;

import java.util.List;

public class LFLogger {
    public static void info(String... lines) {
        log("INFO", lines);
    }

    public static void error(Patch patch, Exception e) {
        StackTraceElement[] stackTraceElements = e.getStackTrace();
        String[] stackTraceStrings = new String[stackTraceElements.length + 2];

        stackTraceStrings[0] = patch.getId();
        stackTraceStrings[1] = e.getMessage();

        for (int i = 0; i < stackTraceElements.length; i++) {
            stackTraceStrings[i + 2] = stackTraceElements[i].toString();
        }

        error(stackTraceStrings);
    }

    public static void error(String... lines) {
        log("ERROR", lines);
    }

    public static void log(String prefix, String... lines) {
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                System.out.println("[LF] " + prefix + ": " + lines[i]);
            } else {
                System.out.println("        " + lines[i]);
            }
        }
    }

    public static void logList(String header, List<String> lines) {
        System.out.println("[LF] " + header);
        for (String line : lines) {
            System.out.println("        " + line);
        }
    }
}
