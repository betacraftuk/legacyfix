package uk.betacraft.legacyfix.util;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class JvmUtils {
    public static String getJvmArguments() {
        String name = javaVmName();
        return (contains(name, "Server") ? "-server "
                : contains(name, "Client") ? "-client " : "")
                + joinWithSpace(vmArguments());
    }

    static List<String> vmArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    static boolean contains(String s, String b) {
        return s != null && s.indexOf(b) >= 0;
    }

    static String javaVmName() {
        return System.getProperty("java.vm.name");
    }

    static String joinWithSpace(Collection<String> c) {
        return join(" ", c);
    }

    public static String join(String glue, Iterable<String> strings) {
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
}
