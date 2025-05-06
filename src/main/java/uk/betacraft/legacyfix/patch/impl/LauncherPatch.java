package uk.betacraft.legacyfix.patch.impl;

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
import uk.betacraft.legacyfix.util.FileUtils;
import uk.betacraft.legacyfix.util.HashUtils;
import uk.betacraft.util.Request;
import uk.betacraft.util.RequestUtil;
import uk.betacraft.util.WebData;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.Set;

public class LauncherPatch extends Patch {
    public static boolean applied = false;
    public static String minecraftVersion = null;

    public LauncherPatch() {
        super("launcher", "Patches the main launcher class, instead of having a custom one", true, true);
    }

    public void apply(Instrumentation inst) throws Exception {
        String mainClass = System.getProperty("sun.java.command");
        LFLogger.debug("Main class: ", mainClass);

        if (mainClass == null) {
            throw new PatchException("Main class not found");
        }

        if (mainClass.equals("org.prismlauncher.EntryPoint")) {
            LFLogger.info("Prism Launcher detected, patching!");
            patchPrism(inst);
            applied = true;
        } else if (mainClass.equals("org.multimc.EntryPoint")) {
            LFLogger.info("MultiMC detected, patching!");
            patchMultiMC(inst);
            applied = true;
        }
    }

    private void patchPrism(Instrumentation inst) throws Exception {
        CtClass parametersClass = pool.getOrNull("org.prismlauncher.utils.Parameters");
        if (parametersClass == null) {
            throw new PatchException("Parameters class not found?");
        }

        patch(inst, parametersClass, "getString", "getString", "getList");
    }

    private void patchMultiMC(Instrumentation inst) throws Exception {
        CtClass parametersClass = pool.getOrNull("org.multimc.ParamBucket");
        if (parametersClass == null) {
            throw new PatchException("ParamBucket class not found?");
        }

        patch(inst, parametersClass, "firstSafe", "first", "allSafe");
    }

    private void patch(Instrumentation inst, CtClass parametersClass, String getStringMethodName, String getStringUnsafeMethodName, String getListMethodName) throws Exception {
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

        inst.redefineClasses(new ClassDefinition(Class.forName(parametersClass.getName()), parametersClass.toBytecode()));
    }

    public static void downloadAssetsForPrism() {
        if (LegacyFixLauncher.getAssetIndexPath() != null)
            return;

        String customJarName = null;
        File customJarJsonFile = new File("../patches/customjar.json");
        if (customJarJsonFile.exists()) {
            JSONObject customJarJson;
            try {
                customJarJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(customJarJsonFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("File exists but doesn't?", e);
                return;
            }

            customJarName = customJarJson.getJSONObject("mainJar").getString("MMC-displayname");
        }

        File mmcPackJsonFile = new File("../mmc-pack.json");
        if (!mmcPackJsonFile.exists()) {
            LFLogger.error("Could not find mmc-pack.json of this instance, can't download assets");
            return;
        }

        JSONObject mmcPackJson;
        try {
            mmcPackJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(mmcPackJsonFile))));
        } catch (FileNotFoundException e) {
            LFLogger.error("File exists but doesn't?", e);
            return;
        }

        JSONArray componentsArray = mmcPackJson.getJSONArray("components");
        String version = null;
        for (int i = 0; i < componentsArray.length(); i++) {
            JSONObject component = componentsArray.getJSONObject(i);
            if (!"net.minecraft".equals(component.getString("uid")))
                continue;

            version = component.getString("version");
            break;
        }

        if (version == null)
            return;

        JSONObject assetIndexesJson = new JSONObject(new JSONTokener(new InputStreamReader(LauncherPatch.class.getResourceAsStream("/asset_indexes.json"))));

        String assetIndex = null;
        JSONArray versionDataJson = new JSONArray(new JSONTokener(new InputStreamReader(LauncherPatch.class.getResourceAsStream("/version_data.json"))));
        if (customJarName != null) {
            assetIndex = determineAssetIndex(customJarName, assetIndexesJson, versionDataJson);
        } else {
            minecraftVersion = version;
        }

        if (assetIndex == null) {
            assetIndex = determineAssetIndex(version, assetIndexesJson, versionDataJson);
        } else {
            minecraftVersion = customJarName;
        }

        downloadServerFor1_3Snapshots();

        if (assetIndex == null) {
            LFLogger.info("No matching asset index found for version " + version);
            return;
        }

        JSONObject assetIndexSnippet = assetIndexesJson.getJSONObject(assetIndex);

        File assetIndexFile = new File("../../../assets/indexes/" + assetIndex + ".json");
        boolean valid = false;
        checkValid:
        {
            if (!assetIndexFile.exists())
                break checkValid;

            if (assetIndexFile.length() != assetIndexSnippet.getLong("size"))
                break checkValid;

            String localSha1 = null;
            try {
                localSha1 = HashUtils.sha1(new String(RequestUtil.readInputStream(new FileInputStream(assetIndexFile)), "UTF-8"));
            } catch (Throwable t) {
                LFLogger.error("launcher", "Could not read asset index " + assetIndex);
                LFLogger.error("launcher", t);
                break checkValid;
            }

            if (!assetIndexSnippet.getString("sha1").equals(localSha1))
                break checkValid;

            valid = true;
        }

        JSONObject assetIndexJson = null;
        if (!valid) {
            Request req = new Request();
            req.setUrl(assetIndexSnippet.getString("url"));

            WebData response = RequestUtil.performRawGETRequest(req);
            if (!response.successful()) {
                LFLogger.error("launcher", "Failed to download asset index from: " + req.REQUEST_URL);
                LFLogger.error("launcher", response.toString());
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(assetIndexFile);
                fos.write(response.getData());
                fos.close();
            } catch (Throwable t) {
                LFLogger.error("launcher", "Failed to save asset index to: " + assetIndexFile.getAbsolutePath());
                LFLogger.error("launcher", t);
                return;
            }

            assetIndexJson = new JSONObject(new JSONTokener(new InputStreamReader(new ByteArrayInputStream(response.getData()))));
        }

        if (assetIndexJson == null) {
            try {
                assetIndexJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(assetIndexFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("Could not read local asset index json", e);
                return;
            }
        }

        File assetsDir = assetIndexFile.getParentFile().getParentFile();
        try {
            LegacyFixLauncher.setValue("assetsDir", assetsDir.getCanonicalFile().getAbsolutePath());
        } catch (Throwable t) {
            LFLogger.error("Failed to set assetsDir to canonical path, trying relative");
            LegacyFixLauncher.setValue("assetsDir", assetsDir.getAbsolutePath());
        }

        JSONObject objects = assetIndexJson.getJSONObject("objects");

        Set<String> assetSet = objects.keySet();
        for (String assetId : assetSet) {
            JSONObject asset = objects.getJSONObject(assetId);
            String hash = asset.getString("hash");
            String hashPath = "/" + hash.substring(0, 2) + "/" + hash;

            File assetFile = new File(assetsDir, "objects" + hashPath);
            if (assetFile.exists() && assetFile.length() == asset.getLong("size"))
                continue;

            Request req = new Request();
            if (asset.has("url")) {
                req.setUrl(asset.getString("url"));
            } else {
                req.setUrl("https://resources.download.minecraft.net" + hashPath);
            }

            LFLogger.info("Downloading asset: '" + assetId + "'");
            if (!RequestUtil.download(req, assetFile)) {
                LFLogger.error("launcher", "Failed to download asset '" + assetId + "' from index '" + assetIndex + "'");
                return;
            }
        }

        LFLogger.info("All assets were downloaded for asset index '" + assetIndex + "'");

        patchNetMinecraftJson(version, assetIndexesJson.getJSONObject(assetIndex));

        File resourcesDir = new File("resources");
        if (resourcesDir.exists() && !LegacyFixAgent.isSetting("keep-resources", "yes"))
            FileUtils.removeRecursively(resourcesDir, false, false);
    }

    private static void patchNetMinecraftJson(String baseVersion, JSONObject assetIndex) {
        if (LegacyFixAgent.isSetting("keep-net.minecraft.json", "yes"))
            return;

        File netMinecraftJsonFile = new File("../patches/net.minecraft.json");
        JSONObject netMinecraftJson;
        if (!netMinecraftJsonFile.exists()) {
            File srcNetMinecraftJsonFile = new File("../../../meta/net.minecraft/" + baseVersion + ".json");

            try {
                netMinecraftJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(srcNetMinecraftJsonFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("Could not read MMC version json", e);
                return;
            }
        } else {
            try {
                netMinecraftJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(netMinecraftJsonFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("Could not read MMC version json", e);
                return;
            }
        }

        netMinecraftJson.remove("assetIndex");
        netMinecraftJson.put("assetIndex", assetIndex);

        try {
            netMinecraftJsonFile.getParentFile().mkdirs();

            FileOutputStream fos = new FileOutputStream(netMinecraftJsonFile);
            fos.write(netMinecraftJson.toString(4).getBytes("UTF-8"));
            fos.close();
        } catch (Throwable t) {
            LFLogger.error("launcher", "Failed to save MMC version json to: " + netMinecraftJsonFile.getAbsolutePath());
            LFLogger.error("launcher", t);
            return;
        }
    }

    private static void downloadServerFor1_3Snapshots() {
        if (!minecraftVersion.startsWith("12w18a") && !minecraftVersion.startsWith("12w19a") && !minecraftVersion.startsWith("12w21a"))
            return;

        String actualVersion = minecraftVersion.substring(0, 6);

        File serverJarFile = new File("server/minecraft_server.jar");
        if (serverJarFile.exists()) {
            return;
        }

        serverJarFile.getParentFile().mkdirs();

        Request req = new Request();
        req.setUrl("https://vault.omniarchive.uk/archive/java/server-release/1.3/pre/" + actualVersion + ".jar");

        LFLogger.info("Downloading server for: '" + actualVersion + "'");
        if (!RequestUtil.download(req, serverJarFile)) {
            LFLogger.error("launcher", "Failed to download server for '" + actualVersion + "'");
        }
    }

    private static String determineAssetIndex(String version, JSONObject assetIndexesJson, JSONArray versionDataJson) {
        for (int i = 0; i < versionDataJson.length(); i++) {
            JSONObject versionData = versionDataJson.getJSONObject(i);
            if (!version.matches(versionData.getString("version")))
                continue;

            String assetIndex = versionData.getString("assetIndex");
            LegacyFixLauncher.setValue("assetIndex", assetIndex);

            if (!assetIndexesJson.has(assetIndex)) {
                LFLogger.error("launcher", "No '" + assetIndex + "' in asset_indexes.json");
                return null;
            }

            if (versionData.has("settings")) {
                JSONArray settings = versionData.getJSONArray("settings");

                for (int j = 0; j < settings.length(); j++) {
                    String[] setting = settings.getString(j).split("=", 2);

                    if (setting[0].startsWith("--")) {
                        LegacyFixLauncher.setValue(setting[0].substring(2), setting.length == 2 ? setting[1] : null);
                    } else {
                        System.setProperty(setting[0], setting.length == 2 ? setting[1] : "");

                        if (LegacyFixAgent.getSettings().containsKey(setting[0]))
                            LegacyFixAgent.getSettings().remove(setting[0]);

                        LegacyFixAgent.getSettings().put(setting[0], setting.length == 2 ? setting[1] : "");
                    }
                }
            }

            return assetIndex;
        }

        return null;
    }
}