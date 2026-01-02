package uk.betacraft.legacyfix.fml;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@SuppressWarnings("unused")
public class CpwCoreMod implements IFMLLoadingPlugin {
    @Override
    public String[] getLibraryRequestClass() {
        return null;
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return "uk.betacraft.legacyfix.tweaker.CpwTweaker";
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }
}
