package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class FmlModContainerPatch extends Patch {
    public FmlModContainerPatch() {
        super("fml-mod", "Injects a mod entry into FML when loading from a tweaker.", false, false);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        String loaderClassName;
        String modContainerClassName = "uk.betacraft.legacyfix.fml.";
        if (patchPool.getRawClass("cpw.mods.fml.common.Loader") != null) {
            loaderClassName = "cpw.mods.fml.common.Loader";
            modContainerClassName += "CpwModContainer";
        } else if (patchPool.getRawClass("net.minecraftforge.fml.common.Loader") != null) {
            loaderClassName = "net.minecraftforge.fml.common.Loader";
            modContainerClassName += "FmlModContainer";
        } else {
            throw new PatchException("Could not find Loader class!");
        }

        final String containerClassName = modContainerClassName;
        patchPool.addCtTransformer(loaderClassName, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod identifyMods = ctClass.getDeclaredMethod("identifyMods");
                identifyMods.insertBefore("$0.injectedContainers.add(\"" + containerClassName + "\");");
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return patchPool.getRawClass("cpw.mods.fml.common.Loader") != null || patchPool.getRawClass("net.minecraftforge.fml.common.Loader") != null;
    }
}
