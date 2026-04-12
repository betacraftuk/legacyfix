package uk.betacraft.legacyfix.proxy.api;

import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.util.web.Request;
import uk.betacraft.legacyfix.util.web.RequestUtil;
import uk.betacraft.legacyfix.util.web.WebData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SkinOverrideApi {
    public static final String API_HOST = Agent.getSetting("lf.proxy.overrides.host", "https://betacraft.uk");
    public static final OverrideMode MODE = OverrideMode.fromConfig();
    public static final boolean ENABLED = MODE != OverrideMode.DISABLED;

    public static MinecraftApi.SkinData getSkinOverride(String username) {
        if (!ENABLED) {
            return null;
        }

        if (MODE.isLocalEnabled()) {
            MinecraftApi.SkinData localData = getLocalFileData(username, "skin");
            if (localData != null) {
                return localData;
            }
        }

        if (MODE.isApiEnabled()) {
            return getApiSkinOverride(username);
        }

        return null;
    }

    public static MinecraftApi.SkinData getCapeOverride(String username) {
        if (!ENABLED) {
            return null;
        }

        if (MODE.isLocalEnabled()) {
            MinecraftApi.SkinData localData = getLocalFileData(username, "cape");
            if (localData != null) {
                return localData;
            }
        }

        if (MODE.isApiEnabled()) {
            return getApiCapeOverride(username);
        }

        return null;
    }

    private static MinecraftApi.SkinData getLocalFileData(String username, String type) {
        try {
            File legacyFixDir = new File(GameArgs.getGameDir(), "legacyfix");
            File typeDir = new File(legacyFixDir, type);
            File overrideFile = new File(typeDir, username + ".png");

            if (!overrideFile.exists() || !overrideFile.isFile()) {
                return null;
            }

            byte[] data = readFileFully(overrideFile);
            if (data == null) {
                return null;
            }

            if ("skin".equals(type)) {
                return new MinecraftApi.SkinData(data, null, false);
            } else {
                return new MinecraftApi.SkinData(null, data, false);
            }
        } catch (Throwable t) {
            Logger.error("SkinOverrideApi.getLocalFileData", t);
            return null;
        }
    }

    private static MinecraftApi.SkinData getApiSkinOverride(String username) {
        try {
            WebData data = RequestUtil.performRawGETRequest(new Request().setUrl(API_HOST + "/api/skin/" + username));

            if (!data.successful()) {
                return null;
            }

            return new MinecraftApi.SkinData(data.getData(), null, false);
        } catch (Throwable t) {
            Logger.error("SkinOverrideApi.getApiSkinOverride", t);
            return null;
        }
    }

    private static MinecraftApi.SkinData getApiCapeOverride(String username) {
        try {
            WebData data = RequestUtil.performRawGETRequest(new Request().setUrl(API_HOST + "/api/cape/" + username));

            if (!data.successful()) {
                return null;
            }

            return new MinecraftApi.SkinData(null, data.getData(), false);
        } catch (Throwable t) {
            Logger.error("SkinOverrideApi.getApiCapeOverride", t);
            return null;
        }
    }

    private static byte[] readFileFully(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception e) {
            Logger.error("SkinOverrideApi.readFileFully", e);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
            }
        }
    }

    public enum OverrideMode {
        /// Overrides are completely disabled
        DISABLED,

        /// Overrides are loaded exclusively from the remote API
        API,

        /// Overrides are loaded exclusively from local files
        LOCAL,

        /// Overrides are loaded from local files first, with a fallback to the remote API
        MIXED;

        public static OverrideMode fromConfig() {
            String fallback = Agent.hasSetting("lf.proxy.overrides.disable") ? "DISABLED" : "MIXED";
            String setting = Agent.getSetting("lf.proxy.overrides.mode", fallback);

            try {
                return OverrideMode.valueOf(setting.toUpperCase());
            } catch (IllegalArgumentException e) {
                Logger.error("SkinOverrideApi", "Invalid override mode: " + setting + ". Defaulting to " + fallback);
                return OverrideMode.valueOf(fallback);
            }
        }

        public boolean isLocalEnabled() {
            return this == LOCAL || this == MIXED;
        }

        public boolean isApiEnabled() {
            return this == API || this == MIXED;
        }
    }
}