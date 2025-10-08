package uk.betacraft.legacyfix.patch.impl.launch.launchers;

import javassist.CtClass;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.util.OSUtils;

import java.io.*;
import java.lang.instrument.Instrumentation;

public class PrismPatch extends MultiMCPatch {
    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass parametersClass = pool.getOrNull("org.prismlauncher.utils.Parameters");
        if (parametersClass == null) {
            throw new PatchException("Parameters class not found?");
        }

        this.patch(inst, parametersClass, "getString", "getString", "getList");
        if (!readMinecraftVersionInfo()) {
            throw new PatchException("Failed to read minecraft version info");
        }

        patchOrgLwjglJson();
    }

    private static void patchOrgLwjglJson() {
        if (LegacyFixAgent.hasSetting("lf.keep-org.lwjgl.json")) {
            return;
        }

        if (lwjglVersion == null) {
            return;
        }

        if (!OSUtils.getPlatform().is(OSUtils.OS.MACOS, OSUtils.Arch.AARCH64)) {
            return;
        }

        if (!"2.9.4-nightly-20150209".equals(lwjglVersion)) {
            LFLogger.error("Could not patch LWJGL2!",
                "Required LWJGL 2.9.4-nightly-20150209, got " + lwjglVersion,
                "Change your LWJGL2 version if you want to resize your game without crashing."
            );
            return;
        }

        File orgLwjglJsonFile = new File("../patches/org.lwjgl.json");
        JSONObject orgLwjglJson = readMMCJson(orgLwjglJsonFile, new File("../../../meta/org.lwjgl/" + lwjglVersion + ".json"));
        if (orgLwjglJson == null) {
            return;
        }

        JSONArray libraries = orgLwjglJson.getJSONArray("libraries");
        for (int i = 0; i < libraries.length(); i++) {
            JSONObject library = libraries.getJSONObject(i);

            String libName = library.getString("name");
            String expectedLibName = "org.lwjgl.lwjgl:lwjgl-platform:" + lwjglVersion;
            if (!expectedLibName.equals(libName)) {
                continue;
            }

            JSONObject downloads = library.getJSONObject("downloads");
            JSONObject classifiers = downloads.getJSONObject("classifiers");
            if (!classifiers.has("natives-osx-arm64")) {
                return;
            }

            JSONObject osxArm64Natives = classifiers.getJSONObject("natives-osx-arm64");
            String brokenNativesUrl = "https://github.com/MinecraftMachina/lwjgl/releases/download/2.9.4-20150209-mmachina.2/lwjgl-platform-2.9.4-nightly-20150209-natives-osx.jar";
            // if the natives are already modified by something else than LF, don't overwrite them.
            if (!brokenNativesUrl.equals(osxArm64Natives.getString("url"))) {
                return;
            }

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
}
