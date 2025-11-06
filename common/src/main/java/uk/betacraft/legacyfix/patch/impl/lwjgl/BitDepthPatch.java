package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class BitDepthPatch extends Patch {
    public BitDepthPatch() {
        super("bitdepth", "Fixes torn clouds on AMD GPUs", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        CtClass displayClass = patchPool.getClass("org.lwjgl.opengl.Display");
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

        patchPool.patchClass(displayClass);
    }
}
