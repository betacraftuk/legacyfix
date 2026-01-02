package uk.betacraft.legacyfix.agent;

import uk.betacraft.legacyfix.Logger;

public class FmlInjector {
    public static boolean inject() {
        try {
            Class.forName("cpw.mods.fml.relauncher.FMLCorePlugin");

            String cliCoremods = System.getProperty("fml.coreMods.load", "");
            if (!"".equals(cliCoremods)) {
                cliCoremods += ",";
            }
            cliCoremods += "uk.betacraft.legacyfix.fml.CpwCoreMod";
            System.setProperty("fml.coreMods.load", cliCoremods);

            return true;
        } catch (Exception e) {
            if (!(e instanceof ClassNotFoundException)) {
                Logger.error("Error injecting into LaunchWrapper", e);
            }

            return false;
        }
    }
}