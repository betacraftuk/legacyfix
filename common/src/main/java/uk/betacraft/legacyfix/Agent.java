package uk.betacraft.legacyfix;

import javassist.*;
import uk.betacraft.legacyfix.agent.FmlInjector;
import uk.betacraft.legacyfix.agent.LaunchWrapperInjector;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.patch.impl.java.JavaModulesPatch;
import uk.betacraft.legacyfix.util.BouncyCastleUtils;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;

public class Agent {
    public static final boolean DEBUG;
    public static final String VERSION;
    private static final Map<String, Object> SETTINGS = new HashMap<String, Object>();

    public static boolean loaded = false;

    public static void premain(String agentArgs, final Instrumentation inst) {
        loaded = true;
        Logger.info("Loading agent (" + VERSION + ")");

        if (LaunchWrapperInjector.inject(inst)) {
            Logger.info("Injected LaunchWrapper transformer");
            return;
        }

        if (FmlInjector.inject()) {
            Logger.info("Injected legacy FML transformer");
            return;
        }

        if (Agent.useBouncyCastle()) {
            BouncyCastleUtils.init();
        }

        Patcher patcher = new Patcher(ClassPool.getDefault());
        patcher.patches.add(new JavaModulesPatch());
        patcher.apply();

        try {
            for (Map.Entry<String, byte[]> transformed : patcher.getTransformedClasses().entrySet()) {
                inst.redefineClasses(new ClassDefinition(Class.forName(transformed.getKey()), transformed.getValue()));
            }
        } catch (Exception e) {
            Logger.error("Failed to redefine classes!", e);
        }

        for (final Transformer transformer : patcher.getTransformers()) {
            inst.addTransformer(new ClassFileTransformer() {
                public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                    try {
                        return transformer.transform(className.replace('/', '.'), classfileBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to apply transformer on class \"" + className + "\"", e);
                    }
                }
            });
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
