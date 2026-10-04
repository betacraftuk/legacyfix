package uk.betacraft.legacyfix.util.launchers;

import org.json.JSONArray;
import org.json.JSONObject;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.util.OSUtils;

import java.io.File;
import java.util.Iterator;

public class PrismLauncherUtils extends MultiMCUtils {
    private static final String VERSIONS_WITH_ASM4_REGEX = "^1\\.([3-6](?:.*)?|7\\.2)$";

    public static boolean detect() {
        return System.getProperty("sun.java.command") != null && System.getProperty("sun.java.command").equals("org.prismlauncher.EntryPoint");
    }

    public static boolean setup() throws Exception {
        MMCVersionInfo mmcInfo = readMMCVersion();
        if (mmcInfo == null) {
            throw new Exception("Failed to read MMC version info");
        }

        boolean mcJson = patchMinecraftJson(mmcInfo);
        boolean lwjglJson = patchLwjglJson(mmcInfo);

        return mcJson || lwjglJson;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected static boolean shouldPatchLwjgl(MMCVersionInfo mmcVersionInfo) {
        if (!detect()) {
            return false;
        }

        if (Agent.hasSetting("lf.keep-org.lwjgl.json")) {
            return false;
        }

        if (mmcVersionInfo.lwjglVersion == null) {
            return false;
        }

        String mcVersion = Agent.getSetting("lf.version", "");
        if (mmcVersionInfo.forgeVersion != null && mcVersion.matches(VERSIONS_WITH_ASM4_REGEX)) {
            // asm 4 can't handle Java 8 bytecode, so for now we won't apply Legacy Fabric LWJGL for Forge instances with these versions
            return false;
        }

        return OSUtils.getPlatform().is(OSUtils.OS.MACOS, OSUtils.Arch.AARCH64);
    }

    protected static boolean patchLwjglJson(MMCVersionInfo mmcVersionInfo) {
        if (!shouldPatchLwjgl(mmcVersionInfo)) {
            return false;
        }

        File orgLwjglJsonFile = new File("../patches/org.lwjgl.json");
        JSONObject orgLwjglJson = readMMCJson(orgLwjglJsonFile, new File("../../../meta/org.lwjgl/" + mmcVersionInfo.lwjglVersion + ".json"));
        if (orgLwjglJson == null) {
            return false;
        }

        JSONArray libraries = orgLwjglJson.getJSONArray("libraries");

        Iterator<Object> it = libraries.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            if (!(obj instanceof JSONObject)) {
                continue;
            }
            JSONObject library = (JSONObject) obj;

            String libName = library.getString("name");
            String expectedLibName = "org.lwjgl.lwjgl:";

            if (libName == null) {
                continue;
            }

            if (!libName.startsWith(expectedLibName)) {
                continue;
            }

            if (libName.endsWith(":" + LWJGL_LEGACYFABRIC_VERSION)) {
                Logger.debug("patchLwjglJson", "'org.lwjgl.json' has been already patched");
                return false;
            }

            it.remove();
        }

        JSONObject lwjglJson = new JSONObject();
        lwjglJson.put("name", "org.lwjgl.lwjgl:lwjgl:" + LWJGL_LEGACYFABRIC_VERSION);
        lwjglJson.put("url", "https://maven.legacyfabric.net/");

        JSONObject lwjglUtilJson = new JSONObject();
        lwjglUtilJson.put("name", "org.lwjgl.lwjgl:lwjgl_util:" + LWJGL_LEGACYFABRIC_VERSION);
        lwjglUtilJson.put("url", "https://maven.legacyfabric.net/");

        JSONObject lwjglPlatformJson = new JSONObject();
        lwjglPlatformJson.put("name", "org.lwjgl.lwjgl:lwjgl-platform:" + LWJGL_LEGACYFABRIC_VERSION);
        lwjglPlatformJson.put("url", "https://maven.legacyfabric.net/");
        JSONArray excludeArray = new JSONArray();
        excludeArray.put("META-INF/");
        JSONObject extractJson = new JSONObject();
        extractJson.put("exclude", excludeArray);
        lwjglPlatformJson.put("extract", extractJson);
        JSONObject nativesJson = new JSONObject();
        nativesJson.put("osx-arm64", "natives-osx"); // we only care about macOS for now
        lwjglPlatformJson.put("natives", nativesJson);

        libraries.put(lwjglJson);
        libraries.put(lwjglUtilJson);
        libraries.put(lwjglPlatformJson);

        orgLwjglJson.put("libraries", libraries);
        orgLwjglJson.put("version", LWJGL_LEGACYFABRIC_VERSION);

        saveMMCJson(orgLwjglJsonFile, orgLwjglJson);

        Logger.debug("patchLwjglJson", "Patched Prism to use LWJGL2 " + LWJGL_LEGACYFABRIC_VERSION);
        return true;
    }
}