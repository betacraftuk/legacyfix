package cpw.mods.fml.relauncher;

import java.util.Map;

public interface IFMLLoadingPlugin {
    String[] getLibraryRequestClass();
    String getModContainerClass();
    String[] getASMTransformerClass();
    String getSetupClass();
    void injectData(Map<String, Object> data);
}
