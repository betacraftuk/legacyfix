package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class VSyncPatch extends Patch {
    public VSyncPatch() {
        super("vsync", "Enables V-Sync", false);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        CtClass clazz = patchPool.getClass("org.lwjgl.opengl.Display");
        if (clazz.isFrozen()) {
            clazz.defrost();
        }

        CtMethod createMethod = clazz.getDeclaredMethod("create");
        createMethod.insertBefore("setVSyncEnabled(true);");

        patchPool.patchClass(clazz);
    }
}
