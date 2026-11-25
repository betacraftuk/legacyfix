package uk.betacraft.legacyfix.agent;

import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.ConstPool;
import uk.betacraft.legacyfix.Logger;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class FmlInjector {
    private static final String
        LIBRARY_MANAGER_CLASS = "cpw.mods.fml.relauncher.RelaunchLibraryManager",
        COREMOD_CLASS = "uk.betacraft.legacyfix.fml.CpwCoreMod",
        COREMODS_PROP = "fml.coreMods.load";

    public static boolean inject(Instrumentation inst) {
        try {
            Class.forName("cpw.mods.fml.relauncher.FMLCorePlugin");

            if (supportsCoreModsProperty()) {
                String cliCoremods = System.getProperty(COREMODS_PROP, "");
                if (!"".equals(cliCoremods)) {
                    cliCoremods += ",";
                }
                cliCoremods += COREMOD_CLASS;
                System.setProperty(COREMODS_PROP, cliCoremods);
            } else {
                // very early FML
                injectRootPlugin(inst);
            }

            return true;
        } catch (Exception e) {
            if (!(e instanceof ClassNotFoundException)) {
                Logger.error("Error injecting into FML", e);
            }

            return false;
        }
    }

    private static boolean supportsCoreModsProperty() {
        CtClass libraryManager = ClassPool.getDefault().getOrNull(LIBRARY_MANAGER_CLASS);
        if (libraryManager == null) {
            return true;
        }

        ConstPool constPool = libraryManager.getClassFile2().getConstPool();
        for (int i = 1; i < constPool.getSize(); i++) {
            if (constPool.getTag(i) == ConstPool.CONST_String
                && COREMODS_PROP.equals(constPool.getStringInfo(i))
            ) {
                return true;
            }
        }

        return false;
    }

    private static void injectRootPlugin(final Instrumentation inst) {
        final String internalName = LIBRARY_MANAGER_CLASS.replace('.', '/');

        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (!internalName.equals(className)) {
                    return null;
                }

                try {
                    ClassPool pool = new ClassPool(true);
                    pool.insertClassPath(new ByteArrayClassPath(LIBRARY_MANAGER_CLASS, classfileBuffer));
                    CtClass ctClass = pool.get(LIBRARY_MANAGER_CLASS);

                    ctClass.getClassInitializer().insertAfter("" +
                        "{" +
                        "   String[] plugins = new String[rootPlugins.length + 1];" +
                        "   System.arraycopy(rootPlugins, 0, plugins, 0, rootPlugins.length);" +
                        "   plugins[rootPlugins.length] = \"" + COREMOD_CLASS + "\";" +
                        "   rootPlugins = plugins;" +
                        "}"
                    );

                    byte[] bytes = ctClass.toBytecode();
                    ctClass.detach();
                    return bytes;
                } catch (Throwable t) {
                    Logger.error("Failed to inject LegacyFix into FML root plugins", t);
                    return null;
                } finally {
                    inst.removeTransformer(this);
                }
            }
        });
    }
}
