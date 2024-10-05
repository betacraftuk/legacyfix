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

    public static void premain(String agentArgs, final Instrumentation inst) {
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
                new Java6PreclassicPatch(),
                new Java6ReferencesPatch(),
                new SeecretSaturdayPatch(),
                new IndevSoundPatch(),
                new BetaForgePatch(),
                new ModloaderPatch(),
                new GameDirPatch(),
                new CloudPatch(),
                new DeAwtPatch(),
                new MousePatch(),
                new C15aPatch()
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

    public static boolean isDebug() {
        return getSettings().containsKey("lf.debug");
    }

    public static String getGameDir() {
        return getSettings().containsKey("lf.gameDir") ? (String) getSettings().get("lf.gameDir") : "minecraft";
    }

    public static String getAssetsDir() {
        return getSettings().containsKey("lf.assetsDir") ? (String) getSettings().get("lf.assetsDir") : "assets";
    }
}
