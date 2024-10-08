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
    private static final List<Patch> PATCHES = new ArrayList<Patch>();

    private static final JSONObject RELEASE_INFO = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(LegacyFixAgent.class.getResourceAsStream("/releaseInfo.json")))));
    public static final String VERSION = RELEASE_INFO.optString("version", "unknown");

    private static Boolean debug;

    public static void premain(String agentArgs, final Instrumentation inst) {
        LFLogger.info("Loading build " + VERSION);

        for (Map.Entry<Object, Object> property : System.getProperties().entrySet()) {
            String propertyKey = String.valueOf(property.getKey());
            if (propertyKey.startsWith("lf.") && !SETTINGS.containsKey(propertyKey)) {
                SETTINGS.put(propertyKey, property.getValue());
            }
        }

        // TODO: inf-0321 patch
        PATCHES.addAll(Arrays.asList(
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
                new GameDirPatch(),
                new DeAwtPatch(),
                new MousePatch(),
                new VSyncPatch()
        ));

        List<String> patchStates = new ArrayList<String>();
        for (Patch patch : PATCHES) {
            if (!patch.shouldApply()) continue;

            try {
                patch.apply(inst);
                patchStates.add(patch.getId() + " - Applied");
            } catch (PatchException e) {
                patchStates.add(patch.getId() + " - Error: " + e.getMessage());
            } catch (Exception e) {
                patchStates.add(patch.getId() + " - Exception, see stacktrace");
                LFLogger.error(patch, e);
            }
        }

        if (!patchStates.isEmpty()) {
            LFLogger.logList("Patches:", patchStates);
        } else {
            LFLogger.log("No patches applied");
        }
    }

    public static Map<String, Object> getSettings() {
        return SETTINGS;
    }

    public static String getSetting(String key, String alt) {
        return getSettings().containsKey(key) ? (String) getSettings().get(key) : alt;
    }

    public static boolean isDebug() {
        if (debug == null)
            debug = getSettings().containsKey("lf.debug");

        return debug;
    }

    public static String getGameDir() {
        return getSetting("lf.gameDir", "minecraft");
    }

    public static String getAssetsDir() {
        return getSetting("lf.assetsDir", "assets");
    }
}
