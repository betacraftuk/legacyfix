package uk.betacraft.legacyfix.patch.impl.launch;

import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.impl.launch.launchers.MultiMCPatch;
import uk.betacraft.legacyfix.patch.impl.launch.launchers.PrismPatch;

import java.lang.instrument.Instrumentation;

public class LauncherPatch extends Patch {
    private static LauncherType launcherType = LauncherType.UNKNOWN;

    public LauncherPatch() {
        super("launcher", "Hooks launch classes for arguments and patches launcher JSONs", true, true);
    }

    public void apply(Instrumentation inst) throws Exception {
        String mainClass = System.getProperty("sun.java.command");
        LFLogger.debug("Main class: ", mainClass);

        if (mainClass == null) {
            throw new PatchException("Main class not found");
        }

        if (mainClass.equals("org.prismlauncher.EntryPoint")) {
            launcherType = LauncherType.PRISM;
            new PrismPatch().apply(inst);
        } else if (mainClass.equals("org.multimc.EntryPoint")) {
            launcherType = LauncherType.MULTIMC;
            new MultiMCPatch().apply(inst);
        } else if (mainClass.equals("uk.betacraft.legacyfix.LegacyFixLauncher")) {
            launcherType = LauncherType.LEGACYFIX;
        } else if (mainClass.contains("net.minecraft.launchwrapper.Launch")) {
            launcherType = LauncherType.LAUNCHWRAPPER;
        } else {
            throw new PatchException("Unknown main launch class: " + mainClass);
        }
    }

    public static LauncherType getLauncherType() {
        return launcherType;
    }
}