package uk.betacraft.legacyfix.agent;

import java.io.File;
import uk.betacraft.legacyfix.Logger;

public class FabricInjector {
    public static boolean inject() {
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");

            File agentFile = new File(FabricInjector.class.getProtectionDomain().getCodeSource().getLocation().toURI());

            String addMods = System.getProperty("fabric.addMods", "");
            if (!"".equals(addMods)) {
                addMods += File.pathSeparator;
            }
            addMods += agentFile.getAbsolutePath();
            System.setProperty("fabric.addMods", addMods);

            return true;
        } catch (Exception e) {
            if (!(e instanceof ClassNotFoundException)) {
                Logger.error("Error injecting into Fabric", e);
            }
            return false;
        }
    }
}