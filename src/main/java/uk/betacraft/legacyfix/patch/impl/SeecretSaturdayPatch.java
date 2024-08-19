package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchHelper;

/**
 * Fixes a1.1.1's gray screen
 */
public class SeecretSaturdayPatch extends Patch {
    public SeecretSaturdayPatch() {
        super("a1.1.1", "Patches Alpha v1.1.1 to not gray screen", true);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");

        if (displayClass.isFrozen()) {
            displayClass.defrost();
        }

        CtMethod theProblemMethod = displayClass.getDeclaredMethod("setDisplayConfiguration", new CtClass[]{PatchHelper.floatClass, PatchHelper.floatClass, PatchHelper.floatClass});
        theProblemMethod.setBody(
                "{ return; }"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(displayClass.getName()), displayClass.toBytecode()));
    }
}
