package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class VSyncPatch extends Patch {
    public VSyncPatch() {
        super("vsync", "Enables V-Sync", false);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addCtTransformer("org.lwjgl.opengl.Display", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod createMethod = ctClass.getDeclaredMethod("create");
                createMethod.insertBefore("setVSyncEnabled(true);");
            }
        });
    }
}
