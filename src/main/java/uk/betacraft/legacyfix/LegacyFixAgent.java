package uk.betacraft.legacyfix;

import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.impl.classic.*;
import uk.betacraft.legacyfix.patch.impl.deawt.*;
import uk.betacraft.legacyfix.patch.impl.game.*;
import uk.betacraft.legacyfix.patch.impl.java.*;
import uk.betacraft.legacyfix.patch.impl.launch.*;
import uk.betacraft.legacyfix.patch.impl.lwjgl.*;
import uk.betacraft.legacyfix.patch.impl.thirdparty.*;
import uk.betacraft.legacyfix.util.BouncyCastleUtils;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.util.*;

public class LegacyFixAgent {
    public static final String VERSION;
    private static final Map<String, Object> SETTINGS = new HashMap<String, Object>();
    private static final Patch[] PATCHES;

    private static Boolean debug;

    public static void premain(String agentArgs, final Instrumentation inst) {
        LFLogger.info("Loading build " + VERSION);

        if (LegacyFixAgent.shouldUseBouncyCastle()) {
            BouncyCastleUtils.init();
        }

        List<String> patchStates = new ArrayList<String>();
        for (Patch patch : PATCHES) {
            if (!patch.shouldApply()) {
                continue;
            }

            try {
                patch.apply(inst);
                patchStates.add(patch.getId() + " - Applied");
            } catch (Throwable e) {
                if (e instanceof PatchException) {
                    patchStates.add(patch.getId() + " - Error: " + e.getMessage());
                } else {
                    patchStates.add(patch.getId() + " - Exception, see stacktrace");
                    LFLogger.error(patch, e);
                }

                if (patch.isRequired()) {
                    LFLogger.error("Patch " + patch.getId() + " is required, but failed to apply. Exiting.");
                    System.exit(-1);
                }
            }
        }

        if (!patchStates.isEmpty()) {
            LFLogger.logList("Patches:", patchStates);
        } else {
            LFLogger.info("No patches applied");
        }
    }

    public static Map<String, Object> getSettings() {
        return SETTINGS;
    }

    public static String getSetting(String key, String alt) {
        return getSettings().containsKey(key) ? (String) getSettings().get(key) : alt;
    }

    public static boolean hasSetting(String key) {
        return getSettings().containsKey(key);
    }

    private static Boolean hasBouncyCastle;

    public static boolean hasBouncyCastle() {
        if (hasBouncyCastle != null) {
            return hasBouncyCastle;
        }

        try {
            Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider"); // prov
            Class.forName("org.bouncycastle.oer.BitBuilder"); // util
            Class.forName("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider"); // tls
            return hasBouncyCastle = true;
        } catch (LinkageError ignored) {
            return hasBouncyCastle = false;
        } catch (ClassNotFoundException ignored) {
            return hasBouncyCastle = false;
        }
    }

    public static boolean shouldUseBouncyCastle() {
        if (hasSetting("lf.bouncycastle")) {
            if (!hasBouncyCastle()) {
                LFLogger.error("Cannot use Bouncy Castle for TLS -- no Bouncy Castle libraries in classpath");
                return false;
            }
            return true;
        }

        if (hasSetting("lf.bouncycastle.disable")) {
            return false;
        }

        if (!JvmUtils.canThisDoModernTLS()) {
            if (hasBouncyCastle()) {
                return true;
            }

            JvmUtils.printTLSWarning();
        }
        return false;
    }

    public static boolean isDebug() {
        if (debug == null) {
            debug = hasSetting("lf.debug");
        }

        return debug;
    }

    static {
        String version;
        try {
            version = new BufferedReader(new InputStreamReader(LegacyFixAgent.class.getResourceAsStream("/version.txt"))).readLine();
        } catch (Throwable ignored) {
            version = "unknown";
        }
        VERSION = version;

        for (Map.Entry<Object, Object> property : System.getProperties().entrySet()) {
            String propertyKey = String.valueOf(property.getKey());
            if (propertyKey.startsWith("lf.") && !SETTINGS.containsKey(propertyKey)) {
                SETTINGS.put(propertyKey, property.getValue());
            }
        }

        PATCHES = new Patch[]{
            new LauncherPatch(),
            new VisualizerPatch(),
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
            new ClassicIndevResizePatch(),
            new ClassicProgressRendererPatch(),
            new GameDirPatch(),
            new ScreenshotPatch(),
            new IntelPatch(),
            new DeAwtPatch(),
            new MousePatch(),
            new VSyncPatch()
        };
    }
}
