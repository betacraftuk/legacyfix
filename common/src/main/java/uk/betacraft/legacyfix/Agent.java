package uk.betacraft.legacyfix;

import javassist.*;
import uk.betacraft.legacyfix.agent.FabricInjector;
import uk.betacraft.legacyfix.agent.FmlInjector;
import uk.betacraft.legacyfix.agent.LaunchWrapperInjector;
import uk.betacraft.legacyfix.jvm.AppletPolyfill;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.impl.misc.GameDirPatch;
import uk.betacraft.legacyfix.util.BouncyCastleUtils;
import uk.betacraft.legacyfix.util.JvmUtils;
import uk.betacraft.legacyfix.util.StreamUtils;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.List;

public class Agent {
    public static final boolean DEBUG;
    public static final String VERSION;
    private static final Map<String, Object> SETTINGS = new HashMap<String, Object>();

    public static boolean loaded = false;
    public static boolean active = false;
    private static final String JVM_PATCHES_PROP = "lf.agent.jvmPatches";
    private static Instrumentation lwInst;

    public static void premain(String agentArgs, final Instrumentation inst) {
        loaded = true;
        Logger.info("Loading agent (" + VERSION + ")");

        if (JvmUtils.getJvmVersion() >= 26) {
            try {
                AppletPolyfill.install();
                Logger.info("Installed applet polyfill");
            } catch (Throwable t) {
                Logger.error("Failed to install applet polyfill", t);
            }
        }

        if (LaunchWrapperInjector.inject(inst)) {
            Logger.info("Injected LaunchWrapper transformer");
            lwInst = inst;
            System.setProperty(JVM_PATCHES_PROP, "true");
            install(inst, new Patcher(ClassPool.getDefault(), Patcher.JVM_PATCHES));
            return;
        }

        if (FmlInjector.inject(inst)) {
            Logger.info("Injected legacy FML transformer");
            installGameDirPatch(inst);
            return;
        }

        if (FabricInjector.inject()) {
            Logger.info("Injected Fabric mod");
            installGameDirPatch(inst);
            return;
        }

        if (Agent.useBouncyCastle()) {
            BouncyCastleUtils.init();
        }

        active = true;
        install(inst, new Patcher(ClassPool.getDefault()));
    }

    private static void installGameDirPatch(Instrumentation inst) {
        install(inst, new Patcher(ClassPool.getDefault(), new Patch[]{new GameDirPatch()}));
    }

    private static void install(Instrumentation inst, final Patcher patcher) {
        patcher.apply();

        Map<String, List<CtTransformer>> ctTransformersMap = patcher.getCtTransformers();
        List<ClassDefinition> definitions = new ArrayList<ClassDefinition>();

        final Set<String> redefinedClasses = new HashSet<String>();
        Map<String, Class<?>> alreadyLoaded = new HashMap<String, Class<?>>();
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            ClassLoader loader = clazz.getClassLoader();
            if ((loader == null || loader == systemLoader) && ctTransformersMap.containsKey(clazz.getName())) {
                alreadyLoaded.put(clazz.getName(), clazz);
            }
        }

        for (String className : ctTransformersMap.keySet()) {
            Class<?> loadedClass = alreadyLoaded.get(className);
            if (loadedClass == null) {
                continue;
            }

            try {
                ClassPool pool = ClassPool.getDefault();
                CtClass ctClass = pool.get(className);
                if (ctClass.isFrozen()) {
                    ctClass.defrost();
                }

                for (CtTransformer transformer : ctTransformersMap.get(className)) {
                    transformer.transform(ctClass);
                }

                byte[] redefined = patcher.applyTransformers(className, ctClass.toBytecode());
                definitions.add(new ClassDefinition(loadedClass, redefined));
                redefinedClasses.add(className);
                ctClass.detach();
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

        List<Class<?>> retransformTargets = findRetransformTargets(inst, patcher, redefinedClasses);

        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className == null) {
                    return null;
                }

                String dotName = className.replace('/', '.');

                // retransforming a class redefined before hands already-patched bytes
                if (classBeingRedefined != null && redefinedClasses.contains(dotName)) {
                    return null;
                }

                byte[] currentBuffer = classfileBuffer;

                List<CtTransformer> ctTransformers = patcher.getCtTransformers().get(dotName);
                if (ctTransformers != null && !ctTransformers.isEmpty()) {
                    try {
                        ClassPool ctPool = new ClassPool(true);
                        ctPool.insertClassPath(new ByteArrayClassPath(dotName, currentBuffer));
                        CtClass ctClass = ctPool.get(dotName);

                        for (CtTransformer ctTransformer : ctTransformers) {
                            ctTransformer.transform(ctClass);
                        }

                        currentBuffer = ctClass.toBytecode();
                        ctClass.detach();
                    } catch (Exception e) {
                        Logger.error("Failed to apply CtTransformers on class \"" + dotName + "\"", e);
                    }
                }

                currentBuffer = patcher.applyTransformers(dotName, currentBuffer);
                return currentBuffer == classfileBuffer ? null : currentBuffer;
            }
        }, true);

        for (Class<?> clazz : retransformTargets) {
            try {
                inst.retransformClasses(clazz);
                Logger.debug("Retransformed already loaded class " + clazz.getName());
            } catch (Throwable t) {
                Logger.error("Failed to retransform already loaded class " + clazz.getName(), t);
            }
        }
    }

    private static List<Class<?>> findRetransformTargets(Instrumentation inst, Patcher patcher, Set<String> redefinedClasses) {
        List<Class<?>> targets = new ArrayList<Class<?>>();
        if (!inst.isRetransformClassesSupported()) {
            return targets;
        }

        Object ownSource = codeSource(Agent.class);
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            ClassLoader loader = clazz.getClassLoader();
            String name = clazz.getName();
            if (loader == null || clazz.isArray() || name.indexOf('/') != -1 // hidden/lambda classes
                || redefinedClasses.contains(name) || !inst.isModifiableClass(clazz)
            ) {
                continue;
            }

            Object source = codeSource(clazz);
            if (source != null && source.equals(ownSource)) {
                continue;
            }

            InputStream in = null;
            try {
                in = loader.getResourceAsStream(name.replace('.', '/') + ".class");
                if (in == null) {
                    continue;
                }

                byte[] original = StreamUtils.readInputStream(in);
                if (original != null && patcher.applyTransformers(name, original) != original) {
                    targets.add(clazz);
                }
            } catch (Throwable ignored) {
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return targets;
    }

    private static Object codeSource(Class<?> clazz) {
        try {
            ProtectionDomain domain = clazz.getProtectionDomain();
            return domain == null || domain.getCodeSource() == null ? null : domain.getCodeSource().getLocation();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void addLaunchWrapperTransformer(ClassFileTransformer transformer) {
        if (lwInst != null) {
            lwInst.addTransformer(transformer);
        }
    }

    public static boolean appliesJvmPatches() {
        return System.getProperty(JVM_PATCHES_PROP) != null;
    }

    private static ImageIcon icon;
    public static synchronized ImageIcon getIcon() {
        if (icon != null) {
            return icon;
        }

        try {
            URL url = Agent.class.getResource("/assets/legacyfix/icon-outlined.png");

            if (url != null) {
                Image image = new ImageIcon(url).getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
                return icon = new ImageIcon(image);
            }
        } catch (Throwable ignored) {
        }
        return icon = new ImageIcon();
    }

    public static Map<String, Object> getSettings() {
        return SETTINGS;
    }

    public static String getSetting(String key) {
        return getSetting(key, null);
    }

    public static String getSetting(String key, String alt) {
        return getSettings().containsKey(key) ? (String) getSettings().get(key) : alt;
    }

    public static int getIntSetting(String key, int alt) {
        Object value = getSettings().get(key);
        if (value == null) {
            return alt;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return Integer.parseInt(String.valueOf(value));
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
        System.getProperties().put(key, value);
    }

    public static void removeSetting(String key) {
        SETTINGS.remove(key);
        System.getProperties().remove(key);
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
