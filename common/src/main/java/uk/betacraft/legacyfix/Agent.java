package uk.betacraft.legacyfix;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.patch.impl.java.ModloaderPatch;
import uk.betacraft.legacyfix.util.BouncyCastleUtils;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;

public class Agent {
    public static final boolean DEBUG;
    public static final String VERSION;
    private static final Map<String, Object> SETTINGS = new HashMap<String, Object>();

    private static Patcher patcher;

    public static void premain(String agentArgs, final Instrumentation inst) {
        Logger.info("Loading agent (" + VERSION + ")");

        injectLaunchWrapperTransformer(inst);

        if (Agent.useBouncyCastle()) {
            BouncyCastleUtils.init();
        }

        patcher = new Patcher(ClassPool.getDefault());
        patcher.patches.add(new ModloaderPatch());
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
                public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                    try {
                        return transformer.transform(className.replace('/', '.'), classfileBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to apply transformer on class \"" + className + "\"", e);
                    }
                }
            });
        }
    }

    private static void injectLaunchWrapperTransformer(Instrumentation inst) {
        try {
            CtClass launchClass = ClassPool.getDefault().get("net.minecraft.launchwrapper.Launch");

            try {
                launchClass.getDeclaredField("blackboard");
                patchNewLw(launchClass, inst);
            } catch (NotFoundException e) {
                patchOldLw(inst);
            }

            Logger.info("Injected LaunchWrapper transformer");
        } catch (Exception e) {
            if (!(e instanceof NotFoundException)) {
                Logger.error("Error injecting into LaunchWrapper", e);
            }
        }
    }

    private static void patchNewLw(CtClass launchClass, Instrumentation inst) throws Exception {
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
    }

    private static void patchOldLw(Instrumentation inst) throws  Exception {
        CtClass vanillaTweakerClass = ClassPool.getDefault().get("net.minecraft.launchwrapper.VanillaTweaker");
        CtMethod m = vanillaTweakerClass.getDeclaredMethod("injectIntoClassLoader");
        m.setBody("" +
            "{" +
            "   Class clClass = Thread.currentThread().getContextClassLoader().loadClass(\"net.minecraft.launchwrapper.LaunchClassLoader\");" +
            "   Class tweakerClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.tweaker.LFTweaker\");" +
            "   Object tweaker = tweakerClass.newInstance();" +
            "   tweakerClass.getMethod(\"injectIntoClassLoader\", new Class[] { clClass }).invoke(tweaker, new Object[] { $1 });" +
            "}"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(vanillaTweakerClass.getName()), vanillaTweakerClass.toBytecode()));
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
