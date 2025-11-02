package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchTransformer;

public class BitDepthPatch extends Patch {
    public BitDepthPatch() {
        super("bitdepth", "Fixes torn clouds on AMD GPUs", true);
    }

    @Override
    public void apply(PatchTransformer transformer) throws Exception {
        CtClass displayClass = transformer.getClass("org.lwjgl.opengl.Display");
        if (displayClass.isFrozen()) {
            displayClass.defrost();
        }

        CtMethod createMethod = displayClass.getDeclaredMethod("create");
        createMethod.setBody("" +
            "{" +
            "   org.lwjgl.opengl.PixelFormat pixelformat = new org.lwjgl.opengl.PixelFormat();" +
            "   create(pixelformat.withDepthBits(24));" +
            "}"
        );

        transformer.patchClass(displayClass);
    }
}
