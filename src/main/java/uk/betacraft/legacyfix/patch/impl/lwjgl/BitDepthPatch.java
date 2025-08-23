package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;

import java.lang.instrument.Instrumentation;

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

        this.redefineClass(inst, displayClass);
    }
}
