package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class FmlModContainerPatch extends Patch {
    public FmlModContainerPatch() {
        super("fml-mod", "Injects a mod entry into FML when loading from a tweaker.", false, false);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        CtClass loaderClass;
        String modContainerClassName = "uk.betacraft.legacyfix.fml.";
        if (patchPool.getClass("cpw.mods.fml.common.Loader") != null) {
            loaderClass = patchPool.getClass("cpw.mods.fml.common.Loader");
            modContainerClassName += "CpwModContainer";
        } else if (patchPool.getClass("net.minecraftforge.fml.common.Loader") != null) {
            loaderClass = patchPool.getClass("net.minecraftforge.fml.common.Loader");
            modContainerClassName += "FmlModContainer";
        } else {
            throw new PatchException("Could not find Loader class!");
        }

        CtMethod identifyMods = loaderClass.getDeclaredMethod("identifyMods");
        identifyMods.insertBefore("$0.injectedContainers.add(\"" + modContainerClassName + "\");");

        patchPool.patchClass(loaderClass);
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return patchPool.getClass("cpw.mods.fml.common.Loader") != null || patchPool.getClass("net.minecraftforge.fml.common.Loader") != null;
    }
}
