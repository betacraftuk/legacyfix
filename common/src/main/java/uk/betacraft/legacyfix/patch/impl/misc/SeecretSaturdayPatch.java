package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class SeecretSaturdayPatch extends Patch {
    public SeecretSaturdayPatch() {
        super("a1.1.1", "Patches Alpha v1.1.1 to not gray screen", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        CtClass clazz = patchPool.getClass("org.lwjgl.opengl.Display");
        if (clazz.isFrozen()) {
            clazz.defrost();
        }

        CtMethod method = clazz.getDeclaredMethod("setDisplayConfiguration", new CtClass[]{Patch.CT_FLOAT, Patch.CT_FLOAT, Patch.CT_FLOAT});
        method.setBody("{ return; }");

        patchPool.patchClass(clazz);
    }
}