package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class VSyncPatch extends Patch {
    public VSyncPatch() {
        super("vsync", "Enables VSync", false);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");

        if (displayClass.isFrozen()) {
            displayClass.defrost();
        }

        CtMethod createMethod = displayClass.getDeclaredMethod("create");
        // @formatter:off
        createMethod.insertBefore(
            "setVSyncEnabled(true);"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(displayClass.getName()), displayClass.toBytecode()));
    }
}
