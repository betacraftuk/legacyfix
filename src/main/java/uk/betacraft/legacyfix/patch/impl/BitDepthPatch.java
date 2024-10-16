package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;

/**
 * Fixes cloud glitches on AMD GPUs
 */
public class BitDepthPatch extends Patch {
    public BitDepthPatch() {
        super("bitdepth", "Fixes torn clouds on AMD GPUs", true);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");

        if (displayClass.isFrozen()) {
            displayClass.defrost();
        }

        CtMethod createMethod = displayClass.getDeclaredMethod("create");
        // @formatter:off
        createMethod.setBody(
            "{" + 
            "   org.lwjgl.opengl.PixelFormat pixelformat = new org.lwjgl.opengl.PixelFormat();" +
            "   create(pixelformat.withDepthBits(24));" + 
            "}"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(displayClass.getName()), displayClass.toBytecode()));
    }
}
