package uk.betacraft.legacyfix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.impl.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.util.*;

public class LegacyFixAgent {
    private static final Map<String, Object> SETTINGS = new HashMap<String, Object>();
    private static final List<Patch> PATCHES = new ArrayList<Patch>();
    private static int jvmVersion = -1;

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HashMap<?, ?> RELEASE_INFO = GSON.fromJson(new BufferedReader(new InputStreamReader(LegacyFixAgent.class.getResourceAsStream("/releaseInfo.json"))), HashMap.class);
    public static final String VERSION = RELEASE_INFO.containsKey("version") ? (String) RELEASE_INFO.get("version") : "unknown";

    public static void premain(String agentArgs, final Instrumentation inst) {
        jvmVersion = getMajorJvmVersion();

        LFLogger.info("Loading build " + VERSION);

        for (Map.Entry<Object, Object> property : System.getProperties().entrySet()) {
            String propertyKey = String.valueOf(property.getKey());
            if (propertyKey.startsWith("lf.") && !SETTINGS.containsKey(propertyKey)) {
                SETTINGS.put(propertyKey, property.getValue());
            }
        }

        PATCHES.addAll(Arrays.asList(
            new DisableControllersPatch(),
            new TexturePackFolderPatch(),
            new ModloaderPatch(),
            new BetaForgePatch(),
            new DeAwtPatch()
        ));

        for (Patch patch : PATCHES) {
            if (patch.shouldApply()) {
                try {
                    if (patch.apply(inst)) {
                        LFLogger.info("Applied " + patch.getName());
                    } else {
                        LFLogger.error("Failed to apply " + patch.getName());
                    }
                } catch (Exception e) {
                    LFLogger.error(patch, e);
                }
            }
        }
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
        return SETTINGS;
    }

    public static int getJvmVersion() {
        return jvmVersion;
    }
}
