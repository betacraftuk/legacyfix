package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class DisableControllersPatch extends Patch {
    public DisableControllersPatch() {
        super("disable-controllers", "Disables controller support as a workaround for freezing on the Mojang screen", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addCtTransformer("org.lwjgl.input.Controllers", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod createMethod = ctClass.getDeclaredMethod("create");
                createMethod.setBody(
                    "{ return; }"
                );
            }
        });
    }
}
