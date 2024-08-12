package uk.betacraft.legacyfix.util;

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
        if (strings == null) return "";
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

        try {
            return Integer.parseInt(javaVersion);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int getJvmVersion() {
        return jvmVersion;
    }
}
