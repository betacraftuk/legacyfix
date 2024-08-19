package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;

/**
 * Fixes cloud glitches on AMD GPUs
 */
public class CloudPatch extends Patch {

    public CloudPatch() {
        super("amdClouds", "Fixes torn clouds on AMD GPUs", true);
    }

    @Override
    public void apply(Instrumentation inst) throws PatchException, Exception {
        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");

        if (displayClass.isFrozen())
            displayClass.defrost();

        CtMethod createMethod = displayClass.getDeclaredMethod("create");
        createMethod.setBody(
                "{" +
                        "   org.lwjgl.opengl.PixelFormat pixelformat = new org.lwjgl.opengl.PixelFormat();" +
                        "   create(pixelformat.withDepthBits(24));" +
                        "}"
        );

        inst.redefineClasses(new ClassDefinition[]{new ClassDefinition(Class.forName(displayClass.getName()), displayClass.toBytecode())});
    }
}
