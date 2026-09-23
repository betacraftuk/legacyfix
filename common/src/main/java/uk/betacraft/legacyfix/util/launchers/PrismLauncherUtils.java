package uk.betacraft.legacyfix.util.launchers;

import org.json.JSONArray;
import org.json.JSONObject;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.util.OSUtils;

import java.io.File;

public class PrismLauncherUtils extends MultiMCUtils {
    public static boolean detect() {
        return System.getProperty("sun.java.command") != null && System.getProperty("sun.java.command").equals("org.prismlauncher.EntryPoint");
    }

    public static boolean setup() throws Exception {
        fetch1_3SnapshotsServer();

        removeResourcesAndAssets();

        MMCVersionInfo mmcInfo = readMMCVersion();
        if (mmcInfo == null) {
            throw new Exception("Failed to read MMC version info");
        }

        findAssetsDir(mmcInfo);

        boolean mcJson = patchMinecraftJson(mmcInfo);
        boolean lwjglJson = patchLwjglJson(mmcInfo);

        return mcJson || lwjglJson;
    }

    protected static boolean patchLwjglJson(MMCVersionInfo mmcVersionInfo) {
        if (Agent.hasSetting("lf.keep-org.lwjgl.json")) {
            return false;
        }

        if (mmcVersionInfo.lwjglVersion == null) {
            return false;
        }

        if (!OSUtils.getPlatform().is(OSUtils.OS.MACOS, OSUtils.Arch.AARCH64)) {
            return false;
        }

        if (!"2.9.4-nightly-20150209".equals(mmcVersionInfo.lwjglVersion)) {
            Logger.error("Could not patch LWJGL2!",
                "Required LWJGL 2.9.4-nightly-20150209, got " + mmcVersionInfo.lwjglVersion,
                "Change your LWJGL2 version if you want to resize your game without crashing."
            );
            return false;
        }

        File orgLwjglJsonFile = new File("../patches/org.lwjgl.json");
        JSONObject orgLwjglJson = readMMCJson(orgLwjglJsonFile, new File("../../../meta/org.lwjgl/" + mmcVersionInfo.lwjglVersion + ".json"));
        if (orgLwjglJson == null) {
            return false;
        }

        JSONArray libraries = orgLwjglJson.getJSONArray("libraries");
        for (int i = 0; i < libraries.length(); i++) {
            JSONObject library = libraries.getJSONObject(i);

            String libName = library.getString("name");
            String expectedLibName = "org.lwjgl.lwjgl:lwjgl-platform:" + mmcVersionInfo.lwjglVersion;

            if ((expectedLibName + "-legacyfix.1").equals(libName)) {
                Logger.debug("'org.lwjgl.json' has been already patched");
                return false;
            }

            if (!expectedLibName.equals(libName)) {
                continue;
            }

            JSONObject downloads = library.getJSONObject("downloads");
            JSONObject classifiers = downloads.getJSONObject("classifiers");
            if (!classifiers.has("natives-osx-arm64")) {
                return false;
            }

            JSONObject osxArm64Natives = classifiers.getJSONObject("natives-osx-arm64");
            String brokenNativesUrl = "https://github.com/MinecraftMachina/lwjgl/releases/download/2.9.4-20150209-mmachina.2/lwjgl-platform-2.9.4-nightly-20150209-natives-osx.jar";
            // if the natives are already modified by something else than LF, don't overwrite them.
            if (!brokenNativesUrl.equals(osxArm64Natives.getString("url"))) {
                return false;
            }

            JSONObject properOSXArm64Natives = new JSONObject();
            properOSXArm64Natives.put("sha1", "a785c8196d3ef960cf420967de2835bef9e2bbb0");
            properOSXArm64Natives.put("size", 500663);
            properOSXArm64Natives.put("url", "https://github.com/Dungeons-Guide/lwjgl/releases/download/2.9.4-20150209-mmachina.2-syeyoung.1/lwjgl-platform-2.9.4-nightly-20150209-natives-osx-arm64.jar");

            classifiers.put("natives-osx-arm64", properOSXArm64Natives);

            downloads.put("classifiers", classifiers);

            library.put("downloads", downloads);
            library.put("name", expectedLibName + "-legacyfix.1");

            libraries.remove(i);
            libraries.put(library);

            orgLwjglJson.put("libraries", libraries);

            saveMMCJson(orgLwjglJsonFile, orgLwjglJson);

            Logger.debug("Patched 'org.lwjgl.json'");
            return true;
        }
        return false;
    }
}
