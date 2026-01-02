package uk.betacraft.legacyfix.agent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class LaunchWrapperInjector {
    public static boolean inject(Instrumentation inst) {
        try {
            CtClass launchClass = ClassPool.getDefault().get("net.minecraft.launchwrapper.Launch");

            try {
                launchClass.getDeclaredField("blackboard");
                patchNewLw(launchClass, inst);
            } catch (NotFoundException e) {
                patchOldLw(inst);
            }

            return true;
        } catch (Exception e) {
            if (!(e instanceof NotFoundException)) {
                Logger.error("Error injecting into LaunchWrapper", e);
            }

            return false;
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
}
