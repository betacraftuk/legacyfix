package uk.betacraft.legacyfix;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.util.BouncyCastleUtils;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.*;

public class Agent {
    public static final boolean DEBUG;
    public static final String VERSION;
    private static final Map<String, Object> SETTINGS = new HashMap<String, Object>();

    private static Patcher patcher;

    public static void premain(String agentArgs, final Instrumentation inst) {
        Logger.info("Loading agent (" + VERSION + ")");

        if (injectLaunchWrapperTransformer(inst)) {
            Logger.info("Injected LaunchWrapper transformer");
            return;
        }

        if (Agent.useBouncyCastle()) {
            BouncyCastleUtils.init();
        }

        patcher = new Patcher(ClassPool.getDefault());
        patcher.apply();

        try {
            for (Map.Entry<String, byte[]> transformed : patcher.getTransformedClasses().entrySet()) {
                inst.redefineClasses(new ClassDefinition(Class.forName(transformed.getKey()), transformed.getValue()));
            }
        } catch (Exception e) {
            Logger.error("Failed to redefine classes!", e);
        }
    }

    private static boolean injectLaunchWrapperTransformer(Instrumentation inst) {
        CtClass launchClass;
        try {
            launchClass = ClassPool.getDefault().get("net.minecraft.launchwrapper.Launch");
        } catch (NotFoundException e) {
            return false;
        }

        try {
            CtMethod m = launchClass.getDeclaredMethod("launch");
            m.instrument(new ExprEditor() {
                public void edit(MethodCall m) throws CannotCompileException {
                    if ("put".equals(m.getMethodName()) && "java.util.Map".equals(m.getClassName())) {
                        m.replace("" +
                            "{" +
                            "   if (\"TweakClasses\".equals($1)) {" +
                            "       ((java.util.List) $2).add(\"uk.betacraft.legacyfix.tweaker.LFTweaker\");" +
                            "   }" +
                            "   $_ = $proceed($$);"+
                            "}"
                        );
                    }
                }
            });

            inst.redefineClasses(new ClassDefinition(Class.forName(launchClass.getName()), launchClass.toBytecode()));
            return true;
        } catch (Exception e) {
            Logger.error("Couldn't inject transformer", e);
            return false;
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

    public static boolean useBouncyCastle() {
        if (hasSetting("lf.bouncycastle")) {
            if (!hasBouncyCastle()) {
                Logger.error("Cannot use Bouncy Castle for TLS -- no Bouncy Castle libraries in classpath");
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

    static {
        String version;
        try {
            version = new BufferedReader(new InputStreamReader(Agent.class.getResourceAsStream("/version.txt"))).readLine();
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

        DEBUG = hasSetting("lf.debug");
    }
}
