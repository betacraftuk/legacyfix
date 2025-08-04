package uk.betacraft.legacyfix;

import org.json.JSONTokener;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.impl.*;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.util.*;

public class LegacyFixAgent {
    private static final Map<String, Object> SETTINGS = new HashMap<String, Object>();
    private static final Patch[] PATCHES;

    private static final JSONObject RELEASE_INFO = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(LegacyFixAgent.class.getResourceAsStream("/release_info.json")))));
    public static final String VERSION = RELEASE_INFO.optString("version", "unknown");

    private static Boolean debug;

    public static void premain(String agentArgs, final Instrumentation inst) {
        LFLogger.info("Loading build " + VERSION);

        List<String> patchStates = new ArrayList<String>();
        for (Patch patch : PATCHES) {
            if (!patch.shouldApply()) continue;

            try {
                patch.apply(inst);
                patchStates.add(patch.getId() + " - Applied");
            } catch (Throwable e) {
                if (e instanceof PatchException) {
                    patchStates.add(patch.getId() + " - Error: " + e.getMessage());
                } else {
                    patchStates.add(patch.getId() + " - Exception, see stacktrace");
                    LFLogger.error(patch, e);
                }

                if (patch.isRequired()) {
                    LFLogger.error("Patch " + patch.getId() + " is required, but failed to apply. Exiting.");
                    System.exit(-1);
                }
            }
        }

        if (!patchStates.isEmpty()) {
            LFLogger.logList("Patches:", patchStates);
        } else {
            LFLogger.info("No patches applied");
        }
    }

    public static Map<String, Object> getSettings() {
        return SETTINGS;
    }

    public static String getSetting(String key, String alt) {
        return getSettings().containsKey(key) ? (String) getSettings().get(key) : alt;
    }

    public static boolean hasSetting(String key) {
        return getSettings().containsKey(key);
    }

    public static boolean isDebug() {
        if (debug == null)
            debug = hasSetting("lf.debug");

        return debug;
    }

    static {
        for (Map.Entry<Object, Object> property : System.getProperties().entrySet()) {
            String propertyKey = String.valueOf(property.getKey());
            if (propertyKey.startsWith("lf.") && !SETTINGS.containsKey(propertyKey)) {
                SETTINGS.put(propertyKey, property.getValue());
            }
        }

        PATCHES = new Patch[]{
                new LauncherPatch(),
                new DisableControllersPatch(),
                new TexturePackFolderPatch(),
                new Java6PreclassicPatch(),
                new Java6ReferencesPatch(),
                new SeecretSaturdayPatch(),
                new LWJGLFramePatch(),
                new IndevSoundPatch(),
                new BetaForgePatch(),
                new ModloaderPatch(),
                new BitDepthPatch(),
                new ClassicPatch(),
                new ClassicIndevResizePatch(),
                new ClassicProgressRendererPatch(),
                new GameDirPatch(),
                new ScreenshotPatch(),
                new IntelPatch(),
                new DeAwtPatch(),
                new MousePatch(),
                new VSyncPatch(),
                new RawInputPatch()
        };
    }
}
