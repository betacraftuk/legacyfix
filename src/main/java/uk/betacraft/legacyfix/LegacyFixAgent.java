package uk.betacraft.legacyfix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.impl.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.util.*;

public class LegacyFixAgent {
    private static final Map<String, Object> SETTINGS = new HashMap<String, Object>();
    private static final List<Patch> PATCHES = new ArrayList<Patch>();

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HashMap<?, ?> RELEASE_INFO = GSON.fromJson(new BufferedReader(new InputStreamReader(LegacyFixAgent.class.getResourceAsStream("/releaseInfo.json"))), HashMap.class);
    public static final String VERSION = RELEASE_INFO.containsKey("version") ? (String) RELEASE_INFO.get("version") : "unknown";

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
}
