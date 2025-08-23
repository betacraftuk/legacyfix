package uk.betacraft.legacyfix.patch.impl.launch.launchers;

import javassist.CtClass;
import javassist.CtMethod;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.LegacyFixLauncher;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;

public class MultiMCPatch extends Patch {
    public static final JSONObject assetIndexesJson = new JSONObject(new JSONTokener(new InputStreamReader(MultiMCPatch.class.getResourceAsStream("/asset_indexes.json"))));
    public static final JSONArray versionDataJson = new JSONArray(new JSONTokener(new InputStreamReader(MultiMCPatch.class.getResourceAsStream("/version_data.json"))));

    public static String minecraftVersion = null;
    public static String baseVersion = null;
    public static String lwjglVersion = null;
    public static String assetIndex = null;

    public void apply(Instrumentation inst) throws Exception {
        CtClass parametersClass = pool.getOrNull("org.multimc.ParamBucket");
        if (parametersClass == null) {
            throw new PatchException("ParamBucket class not found?");
        }

        patch(inst, parametersClass, "firstSafe", "first", "allSafe");
        if (!readMinecraftVersionInfo()) {
            throw new PatchException("Failed to read minecraft version info");
        }
    }

    protected void patch(Instrumentation inst, CtClass parametersClass, String getStringMethodName, String getStringUnsafeMethodName, String getListMethodName) throws Exception {
        if (parametersClass.isFrozen()) {
            parametersClass.defrost();
        }

        CtMethod getStringDefault = parametersClass.getDeclaredMethod(
            getStringMethodName,
            pool.get(new String[]{"java.lang.String", "java.lang.String"})
        );

        CtMethod getList = parametersClass.getDeclaredMethod(
            getListMethodName,
            pool.get(new String[]{"java.lang.String", "java.util.List"})
        );

        //@formatter:off
        getStringDefault.insertBefore(
            "if ($1.equals(\"mainClass\")) { " +
            "    return \"uk.betacraft.legacyfix.LegacyFixLauncher\"; " +
            "}"
        );

        getList.insertAfter(
            "if ($1.equals(\"traits\")) {" +
            "    $_ = new java.util.ArrayList();" +
            "    $_.add(\"noapplet\");" +
            "} else if ($1.equals(\"param\") && $_.size() == 0) {" +
            "    $_.add($0." + getStringUnsafeMethodName + "(\"userName\"));" +
            "    $_.add($0." + getStringUnsafeMethodName + "(\"sessionId\"));" +
            "}"
        );
        //@formatter:on

        this.redefineClass(inst, parametersClass);
    }

    private static String determineAssetIndex(String version) {
        for (int i = 0; i < versionDataJson.length(); i++) {
            JSONObject versionData = versionDataJson.getJSONObject(i);
            if (!version.matches(versionData.getString("version"))) {
                continue;
            }

            String assetIndex = versionData.getString("assetIndex");

            if (versionData.has("settings")) {
                JSONArray settings = versionData.getJSONArray("settings");

                for (int j = 0; j < settings.length(); j++) {
                    String[] setting = settings.getString(j).split("=", 2);

                    if (setting[0].startsWith("--")) {
                        String key = setting[0].substring(2);

                        if (setting.length == 2) {
                            LegacyFixLauncher.setValue(key, setting[1]);
                        } else {
                            LegacyFixLauncher.addKey(key);
                        }
                    } else {
                        System.setProperty(setting[0], setting.length == 2 ? setting[1] : "");

                        if (LegacyFixAgent.hasSetting(setting[0])) {
                            LegacyFixAgent.getSettings().remove(setting[0]);
                        }

                        LegacyFixAgent.getSettings().put(setting[0], setting.length == 2 ? setting[1] : "");
                    }
                }
            }

            if (!assetIndexesJson.has(assetIndex)) {
                LFLogger.debug("launcher", "No '" + assetIndex + "' in asset_indexes.json");
                return null;
            }

            return assetIndex;
        }

        return null;
    }

    public static boolean readMinecraftVersionInfo() {
        String customJarName = null;
        File customJarJsonFile = new File("../patches/customjar.json");
        if (customJarJsonFile.exists()) {
            JSONObject customJarJson;
            try {
                customJarJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(customJarJsonFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("MultiMCPatch", e);
                return false;
            }

            customJarName = customJarJson.getJSONObject("mainJar").getString("MMC-displayname");
        }

        File mmcPackJsonFile = new File("../mmc-pack.json");
        if (!mmcPackJsonFile.exists()) {
            LFLogger.error("Could not find mmc-pack.json of this instance, can't download assets");
            return false;
        }

        JSONObject mmcPackJson;
        try {
            mmcPackJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(mmcPackJsonFile))));
        } catch (FileNotFoundException e) {
            LFLogger.error("MultiMCPatch", e);
            return false;
        }

        JSONArray componentsArray = mmcPackJson.getJSONArray("components");
        for (int i = 0; i < componentsArray.length(); i++) {
            JSONObject component = componentsArray.getJSONObject(i);
            String uid = component.getString("uid");

            if ("net.minecraft".equals(uid)) {
                baseVersion = component.getString("version");
            } else if ("org.lwjgl".equals(uid)) {
                lwjglVersion = component.getString("version");
            }
        }

        if (baseVersion == null) {
            return false;
        }

        if (customJarName != null) {
            assetIndex = determineAssetIndex(customJarName);
        }

        if (assetIndex == null) {
            minecraftVersion = baseVersion;
            assetIndex = determineAssetIndex(baseVersion);

            if (assetIndex == null) {
                LFLogger.info("No matching asset index found for version " + baseVersion);
                return false;
            }
        } else {
            minecraftVersion = customJarName;
        }

        return true;
    }
}