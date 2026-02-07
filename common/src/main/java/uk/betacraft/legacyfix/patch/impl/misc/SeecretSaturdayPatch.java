package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class SeecretSaturdayPatch extends Patch {
    public SeecretSaturdayPatch() {
        super("a1.1.1", "Patches Alpha v1.1.1 to not gray screen", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addCtTransformer("org.lwjgl.opengl.Display", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod method = ctClass.getDeclaredMethod("setDisplayConfiguration", new CtClass[]{Patch.CT_FLOAT, Patch.CT_FLOAT, Patch.CT_FLOAT});
                method.setBody("{ return; }");
            }
        });
    }
}