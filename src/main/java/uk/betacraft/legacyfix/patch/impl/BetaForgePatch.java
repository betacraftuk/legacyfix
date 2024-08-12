package uk.betacraft.legacyfix.patch.impl;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

/**
 * Patches b1.7.3 - b1.8.1 Forge
 */
public class BetaForgePatch extends Patch {
    public BetaForgePatch() {
        super("betaForge", "Beta Forge");
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass clazz = pool.getOrNull("forge.ForgeHooksClient");
        if (clazz == null) throw new PatchException("ForgeHooksClient not found! Is Forge even present?");

        clazz.instrument(new ExprEditor() {
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("toArray") && m.getSignature().equals("()[Ljava/lang/Object;")) {
                    m.replace("$_ = $0.toArray(new Integer[0]);");
                }
            }
        });

        inst.redefineClasses(new ClassDefinition(Class.forName(clazz.getName()), clazz.toBytecode()));
    }
}
