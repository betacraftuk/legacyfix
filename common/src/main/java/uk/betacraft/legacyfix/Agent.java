package uk.betacraft.legacyfix;

import javassist.*;
import uk.betacraft.legacyfix.agent.FabricInjector;
import uk.betacraft.legacyfix.agent.FmlInjector;
import uk.betacraft.legacyfix.agent.LaunchWrapperInjector;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Transformer;
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
    public static boolean active = false;

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

        if (FabricInjector.inject()) {
            Logger.info("Injected Fabric mod");
            return;
        }

        if (Agent.useBouncyCastle()) {
            BouncyCastleUtils.init();
        }

        active = true;
        final Patcher patcher = new Patcher(ClassPool.getDefault());
        patcher.apply();

        Map<String, List<CtTransformer>> ctTransformersMap = patcher.getCtTransformers();
        List<ClassDefinition> definitions = new ArrayList<ClassDefinition>();

        for (String className : ctTransformersMap.keySet()) {
            try {
                Class<?> loadedClass = Class.forName(className);

                ClassPool pool = ClassPool.getDefault();
                CtClass ctClass = pool.get(className);
                if (ctClass.isFrozen()) {
                    ctClass.defrost();
                }

                for (CtTransformer transformer : ctTransformersMap.get(className)) {
                    transformer.transform(ctClass);
                }

                definitions.add(new ClassDefinition(loadedClass, ctClass.toBytecode()));
                ctClass.detach();
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                Logger.error("Failed to prepare redefinition for " + className, e);
            }
        }

        if (!definitions.isEmpty()) {
            try {
                inst.redefineClasses(definitions.toArray(new ClassDefinition[0]));
            } catch (Exception e) {
                Logger.error("Failed to redefine classes!", e);
            }
        }

        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className == null) return null;
                String dotName = className.replace('/', '.');

                byte[] currentBuffer = classfileBuffer;
                boolean modified = false;

                List<CtTransformer> ctTransformers = patcher.getCtTransformers().get(dotName);
                if (ctTransformers != null && !ctTransformers.isEmpty()) {
                    try {
                        ClassPool ctPool = new ClassPool(true);
                        ctPool.appendClassPath(new ByteArrayClassPath(dotName, currentBuffer));
                        CtClass ctClass = ctPool.get(dotName);

                        for (CtTransformer ctTransformer : ctTransformers) {
                            ctTransformer.transform(ctClass);
                        }

                        currentBuffer = ctClass.toBytecode();
                        ctClass.detach();
                        modified = true;
                    } catch (Exception e) {
                        Logger.error("Failed to apply CtTransformers on class \"" + dotName + "\"", e);
                    }
                }

                for (Transformer transformer : patcher.getTransformers()) {
                    try {
                        byte[] result = transformer.transform(dotName, currentBuffer);
                        if (result != null) {
                            currentBuffer = result;
                            modified = true;
                        }
                    } catch (Exception e) {
                        Logger.error("Failed to apply Transformer on class \"" + dotName + "\"", e);
                    }
                }

                return modified ? currentBuffer : null;
            }
        });
    }

    public static Map<String, Object> getSettings() {
        return SETTINGS;
    }

    public static String getSetting(String key, String alt) {
        return getSettings().containsKey(key) ? (String) getSettings().get(key) : alt;
    }

    public static boolean getBooleanSetting(String key, boolean alt) {
        Object value = getSettings().get(key);
        if (value == null) {
            return alt;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static void setSetting(String key, Object value) {
        SETTINGS.put(key, value);
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
