package uk.betacraft.legacyfix.patch.impl.fml;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchTransformer;

public class ForgeModInjectPatch extends Patch {
    public ForgeModInjectPatch() {
        super("fml-mod-inject", "Injects a mod entry into FML when loading from a tweaker.", false, false);
    }

    @Override
    public void apply(PatchTransformer transformer) throws Exception {
        CtClass loaderClass;
        String modContainerClassName = "uk.betacraft.legacyfix.fml.";
        if (transformer.getClass("cpw.mods.fml.common.Loader") != null) {
            loaderClass = transformer.getClass("cpw.mods.fml.common.Loader");
            modContainerClassName += "CpwModContainer";
        } else if (transformer.getClass("net.minecraftforge.fml.common.Loader") != null) {
            loaderClass = transformer.getClass("net.minecraftforge.fml.common.Loader");
            modContainerClassName += "ForgeModContainer";
        } else {
            throw new PatchException("Could not find Loader class!");
        }

        CtMethod identifyMods = loaderClass.getDeclaredMethod("identifyMods");
        identifyMods.insertBefore("{ $0.injectedContainers.add(\"" + modContainerClassName + "\"); }");

        transformer.patchClass(loaderClass);
    }

    @Override
    public boolean shouldApply(PatchTransformer transformer) {
        return transformer.getClass("cpw.mods.fml.common.Loader") != null || transformer.getClass("net.minecraftforge.fml.common.Loader") != null;
    }
}
