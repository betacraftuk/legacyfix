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
import uk.betacraft.legacyfix.util.OSUtils;
import uk.betacraft.util.Request;
import uk.betacraft.util.RequestUtil;
import uk.betacraft.util.WebData;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.Set;

public class LauncherPatch extends Patch {
    public static boolean applied = false;

    public static boolean isPrism = false;
    public static boolean isMultiMC = false;

    public static String minecraftVersion = null;
    public static String baseVersion = null;
    public static String lwjglVersion = null;
    public static String assetIndex = null;

    public static final JSONObject assetIndexesJson = new JSONObject(new JSONTokener(new InputStreamReader(LauncherPatch.class.getResourceAsStream("/asset_indexes.json"))));
    public static final JSONArray versionDataJson = new JSONArray(new JSONTokener(new InputStreamReader(LauncherPatch.class.getResourceAsStream("/version_data.json"))));

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
            isPrism = true;
            LFLogger.info("Prism Launcher detected, patching!");
            patchPrism(inst);
        } else if (mainClass.equals("org.multimc.EntryPoint")) {
            isMultiMC = true;
            LFLogger.info("MultiMC detected, patching!");
            patchMultiMC(inst);
        } else return;

        applied = readMinecraftVersionInfo();
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

    public static boolean readMinecraftVersionInfo() {
        String customJarName = null;
        File customJarJsonFile = new File("../patches/customjar.json");
        if (customJarJsonFile.exists()) {
            JSONObject customJarJson;
            try {
                customJarJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(customJarJsonFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("File exists but doesn't?", e);
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
            LFLogger.error("File exists but doesn't?", e);
            return false;
        }

        JSONArray componentsArray = mmcPackJson.getJSONArray("components");
        for (int i = 0; i < componentsArray.length(); i++) {
            JSONObject component = componentsArray.getJSONObject(i);
            String uid = component.getString("uid");

            if ("net.minecraft".equals(uid))
                baseVersion = component.getString("version");
            else if ("org.lwjgl".equals(uid))
                lwjglVersion = component.getString("version");
        }

        if (baseVersion == null)
            return false;

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

    public static void downloadAssetsForPrism() {
        // don't process asset indexes for versions past 13w48b,
        // or for configurations where the user has specified the asset index,
        // as the asset index should have proper assets already.
        if (LegacyFixLauncher.getAssetIndexPath() != null)
            return;

        downloadServerFor1_3Snapshots();

        File assetIndexFile = new File("../../../assets/indexes/" + assetIndex + ".json");

        JSONObject assetIndexJson = null;
        if (assetIndexesJson.has(assetIndex)) {
            JSONObject assetIndexSnippet = assetIndexesJson.getJSONObject(assetIndex);

            boolean valid = false;
            checkValid:
            {
                if (!assetIndexFile.exists())
                    break checkValid;

                if (assetIndexFile.length() != assetIndexSnippet.getLong("size"))
                    break checkValid;

                String localSha1;
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
        }

        if (assetIndexJson == null) {
            try {
                assetIndexJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(assetIndexFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("Could not read local asset index json", e);
                return;
            }
        }

        LegacyFixLauncher.setValue("assetIndex", assetIndex);

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

        patchNetMinecraftJson();
        patchOrgLwjglJson();

        File resourcesDir = new File("resources");
        if (resourcesDir.exists() && !LegacyFixAgent.hasSetting("lf.keep-resources"))
            FileUtils.removeRecursively(resourcesDir, false, false);
    }

    private static void patchNetMinecraftJson() {
        if (LegacyFixAgent.hasSetting("lf.keep-net.minecraft.json"))
            return;

        File netMinecraftJsonFile = new File("../patches/net.minecraft.json");
        JSONObject netMinecraftJson = readMMCJson(netMinecraftJsonFile, new File("../../../meta/net.minecraft/" + baseVersion + ".json"));
        if (netMinecraftJson == null)
            return;

        netMinecraftJson.remove("assetIndex");
        netMinecraftJson.put("assetIndex", assetIndexesJson.getJSONObject(assetIndex));

        saveMMCJson(netMinecraftJsonFile, netMinecraftJson);

        LFLogger.debug("Patched net.minecraft.json");
    }

    private static void patchOrgLwjglJson() {
        if (!isPrism)
            return;

        if (LegacyFixAgent.hasSetting("lf.keep-org.lwjgl.json"))
            return;

        if (lwjglVersion == null)
            return;

        if (!OSUtils.getPlatform().is(OSUtils.OS.MACOS, OSUtils.Arch.AARCH64))
            return;

        if (!"2.9.4-nightly-20150209".equals(lwjglVersion)) {
            LFLogger.error("Could not patch LWJGL2!",
                    "Required LWJGL 2.9.4-nightly-20150209, got " + lwjglVersion,
                    "Change your LWJGL2 version if you want to resize your game without crashing."
            );
            return;
        }

        File orgLwjglJsonFile = new File("../patches/org.lwjgl.json");
        JSONObject orgLwjglJson = readMMCJson(orgLwjglJsonFile, new File("../../../meta/org.lwjgl/" + lwjglVersion + ".json"));
        if (orgLwjglJson == null)
            return;

        JSONArray libraries = orgLwjglJson.getJSONArray("libraries");
        for (int i = 0; i < libraries.length(); i++) {
            JSONObject library = libraries.getJSONObject(i);

            String libName = library.getString("name");
            String expectedLibName = "org.lwjgl.lwjgl:lwjgl-platform:" + lwjglVersion;
            if (!expectedLibName.equals(libName))
                continue;

            JSONObject downloads = library.getJSONObject("downloads");
            JSONObject classifiers = downloads.getJSONObject("classifiers");
            if (!classifiers.has("natives-osx-arm64"))
                return;

            JSONObject osxArm64Natives = classifiers.getJSONObject("natives-osx-arm64");
            String brokenNativesUrl = "https://github.com/MinecraftMachina/lwjgl/releases/download/2.9.4-20150209-mmachina.2/lwjgl-platform-2.9.4-nightly-20150209-natives-osx.jar";
            // if the natives are already modified by something else than LF, don't overwrite them.
            if (!brokenNativesUrl.equals(osxArm64Natives.getString("url")))
                return;

            JSONObject properOSXArm64Natives = new JSONObject();
            properOSXArm64Natives.put("sha1", "a785c8196d3ef960cf420967de2835bef9e2bbb0");
            properOSXArm64Natives.put("size", 500663);
            properOSXArm64Natives.put("url", "https://github.com/Dungeons-Guide/lwjgl/releases/download/2.9.4-20150209-mmachina.2-syeyoung.1/lwjgl-platform-2.9.4-nightly-20150209-natives-osx-arm64.jar");

            classifiers.remove("natives-osx-arm64");
            classifiers.put("natives-osx-arm64", properOSXArm64Natives);

            downloads.remove("classifiers");
            downloads.put("classifiers", classifiers);

            library.remove("downloads");
            library.put("downloads", downloads);
            library.remove("name");
            library.put("name", expectedLibName + "-legacyfix.1");

            libraries.remove(i);
            libraries.put(library);

            orgLwjglJson.remove("libraries");
            orgLwjglJson.put("libraries", libraries);

            saveMMCJson(orgLwjglJsonFile, orgLwjglJson);

            LFLogger.debug("Patched org.lwjgl.json");
            return;
        }
    }

    private static JSONObject readMMCJson(File mmcJsonFile, File srcMMCJsonFile) {
        JSONObject mmcJson;
        if (!mmcJsonFile.exists()) {
            try {
                mmcJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(srcMMCJsonFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("Could not read MMC json", e);
                return null;
            }
        } else {
            try {
                mmcJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(mmcJsonFile))));
            } catch (FileNotFoundException e) {
                LFLogger.error("Could not read MMC json", e);
                return null;
            }
        }

        return mmcJson;
    }

    private static void saveMMCJson(File mmcJsonFile, JSONObject mmcJson) {
        try {
            mmcJsonFile.getParentFile().mkdirs();

            FileOutputStream fos = new FileOutputStream(mmcJsonFile);
            fos.write(mmcJson.toString(4).getBytes("UTF-8"));
            fos.close();
        } catch (Throwable t) {
            LFLogger.error("launcher", "Failed to save MMC json to: " + mmcJsonFile.getAbsolutePath());
            LFLogger.error("launcher", t);
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

    private static String determineAssetIndex(String version) {
        for (int i = 0; i < versionDataJson.length(); i++) {
            JSONObject versionData = versionDataJson.getJSONObject(i);
            if (!version.matches(versionData.getString("version")))
                continue;

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

                        if (LegacyFixAgent.hasSetting(setting[0]))
                            LegacyFixAgent.getSettings().remove(setting[0]);

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
}