package uk.betacraft.legacyfix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import uk.betacraft.legacyfix.fix.Fix;
import uk.betacraft.legacyfix.fix.impl.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LegacyFixAgent {
    private static Map<String, Object> settings = new HashMap<String, Object>();
    private static List<Fix> fixes = new ArrayList<Fix>();
    private static int jvmVersion = -1;

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HashMap<String, String> releaseInfo = GSON.fromJson(new BufferedReader(new InputStreamReader(LegacyFixAgent.class.getResourceAsStream("/releaseInfo.json"))), HashMap.class);
    public static final String VERSION = releaseInfo.containsKey("version") ? releaseInfo.get("version") : "unknown";

    public static void premain(String agentArgs, final Instrumentation inst) {
        jvmVersion = getMajorJvmVersion();

        LFLogger.info("Loading build " + VERSION);

        for (Map.Entry<Object, Object> property : System.getProperties().entrySet()) {
            if (String.valueOf(property.getKey()).startsWith("lf.")) {
                settings.put(String.valueOf(property.getKey()), property.getValue());
            }
        }

        fixes.add(new ModloaderFix());
        fixes.add(new DeAwtFix());

        for (Fix fix : fixes) {
            if (fix.shouldApply()) {
                if (fix.apply(inst)) {
                    LFLogger.info("Applied " + fix.getName());
                } else {
                    LFLogger.error("Failed to apply " + fix.getName() + "!");
                }
            }
        }
    }

    private static void log(String msg) {
        System.out.println("LF: " + msg);
    }

    private static int getMajorJvmVersion() {
        String jvmVersion = System.getProperty("java.version");

        if (jvmVersion.startsWith("1.")) {
            jvmVersion = jvmVersion.substring(2, 3);
        } else {
            int dot = jvmVersion.indexOf(".");
            if (dot != -1) {
                jvmVersion = jvmVersion.substring(0, dot);
            }
        }

        return Integer.parseInt(jvmVersion);
    }

    public static Map<String, Object> getSettings() {
        return settings;
    }

    public static int getJvmVersion() {
        return jvmVersion;
    }
}
