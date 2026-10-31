package uk.betacraft.legacyfix.patch.impl.java;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

public class WindowOwnerPatch extends Patch {
    public WindowOwnerPatch() {
        super("window-owner", "Fixes Java 5's FileDialog throwing exception when being called by Indev", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addCtTransformer("java.awt.Window", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod createMethod = ctClass.getDeclaredMethod("ownedInit", new  CtClass[] {ctClass});
                createMethod.insertBefore("" +
                    "if ($1 == null) {" +
                    "    return;" +
                    "}"
                );
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() <= 5;
    }
}
