package uk.betacraft.legacyfix.util;

import uk.betacraft.legacyfix.Logger;

import java.lang.management.ManagementFactory;
import java.util.Iterator;

public class JvmUtils {
    private static final int jvmVersion = fetchJvmVersion();

    public static String getJvmArguments() {
        String name = System.getProperty("java.vm.name");
        return (contains(name, "Server") ? "-server "
            : contains(name, "Client") ? "-client " : "")
            + join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    static boolean contains(String s, String b) {
        return s != null && s.contains(b);
    }

    static String join(String glue, Iterable<String> strings) {
        if (strings == null) {
            return "";
        }
        StringBuilder buf = new StringBuilder();
        Iterator<String> i = strings.iterator();
        if (i.hasNext()) {
            buf.append(i.next());
            while (i.hasNext())
                buf.append(glue).append(i.next());
        }
        return buf.toString();
    }

    private static int fetchJvmVersion() {
        String javaVersion = System.getProperty("java.version");

        if (javaVersion.startsWith("1.")) {
            javaVersion = javaVersion.substring(2, 3);
        } else {
            int dot = javaVersion.indexOf(".");
            if (dot != -1) {
                javaVersion = javaVersion.substring(0, dot);
            }
        }

        // trim suffixes like '+b29' and similar
        javaVersion = javaVersion.replaceAll("[^0-9](?:.+)?", "");

        try {
            return Integer.parseInt(javaVersion);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int getJvmVersion() {
        return jvmVersion;
    }

    public static boolean canThisDoModernTLS() {
        if (JvmUtils.getJvmVersion() == 7 && System.getProperty("java.vendor", "").toLowerCase().contains("azul")) {
            // assume only azul's u352 (latest) can connect to our resources
            azulJava7:
            {
                String[] javaVersion = System.getProperty("java.version", "").split("_");
                if (javaVersion.length != 2) {
                    break azulJava7;
                }

                String buildStr = javaVersion[1].replaceAll("[^0-9](?:.+)?", "");
                int build = Integer.parseInt(buildStr);
                if (build >= 352) {
                    return true;
                }
            }
        }

        if (JvmUtils.getJvmVersion() == 8) {
            // assume versions before u401 can't connect to our resources
            oldJava8:
            {
                String[] javaVersion = System.getProperty("java.version", "").split("_");
                if (javaVersion.length != 2) {
                    break oldJava8;
                }

                String buildStr = javaVersion[1].replaceAll("[^0-9](?:.+)?", "");
                int build = Integer.parseInt(buildStr);
                if (build < 401) {
                    return false;
                }
            }
        }

        return JvmUtils.getJvmVersion() >= 8;
    }

    public static void printTLSWarning() {
        String recommendation1;
        String recommendation2;
        String recommendation3 = " https://github.com/betacraftuk/legacyfix/blob/develop/docs/Modern%20TLS%20on%20old%20Java.md";
        String osName = OSUtils.getOSName();
        if (osName.equals("windows xp")) {
            recommendation1 = " Please make your instance use up-to-date Azul Java 7u352.";
            recommendation2 = " Alternatively, please read instructions on how to install a TLS library to your instance to resolve this issue:";
        } else if (OSUtils.isVeryOldWindows()) {
            // technically this branch of code will never trigger for either MultiMC or Prism - their NewLaunch.jar requires Java 7 or later
            // and Betacraft Retro should have it handled out of the box...
            // TODO if we ever come to support the official launcher or derivatives
            recommendation1 = "";
            recommendation2 = " Please read instructions on how to install a TLS library to your instance to resolve this issue:";
        } else {
            recommendation1 = " Please make your instance use up-to-date Java 8 (u401 or newer).";
            recommendation2 = " Alternatively, please read instructions on how to install a TLS library to your instance to resolve this issue:";
        }

        Logger.error("TLS connectivity",
            "------------------------ WARNING ------------------------",
            " You are running outdated Java.",
            " This might result in LegacyFix not being able to function properly (e.g. no sound, no skins, no auth fix).",
            recommendation1,
            recommendation2,
            recommendation3,
            "------------------------ WARNING ------------------------"
        );
    }
}
