package uk.betacraft.legacyfix.patch.impl.java;

import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.lang.reflect.Method;
import java.util.Set;

public class JavaModulesPatch extends Patch {
    public JavaModulesPatch() {
        super("java-modules", "Unlocks internal modules. Required for Java 11+", true, true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        String args = JvmUtils.getJvmArguments();
        if (!args.contains("--add-opens=java.base/java.lang=ALL-UNNAMED")) {
            Logger.error(
                "",
                "LegacyFix can't launch because a required JVM argument is missing.",
                "Please add the following to your JVM options and try again:",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                ""
            );

            throw new PatchException("Conditions not met");
        }

        Class<?> moduleClass = Class.forName("java.lang.Module");
        Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");

        Method getUnnamedModule = ClassLoader.class.getMethod("getUnnamedModule");
        Object unnamedModule = getUnnamedModule.invoke(this.getClass().getClassLoader());

        Method implAddExportsOrOpens = moduleClass.getDeclaredMethod("implAddExportsOrOpens", String.class, moduleClass, boolean.class, boolean.class);
        implAddExportsOrOpens.setAccessible(true);

        Method boot = moduleLayerClass.getMethod("boot");
        Object bootLayer = boot.invoke(null);

        Method getPackages = moduleClass.getMethod("getPackages");
        Method modules = moduleLayerClass.getMethod("modules");
        Set<?> moduleSet = (Set<?>) modules.invoke(bootLayer);

        for (Object module : moduleSet) {
            Set<String> packages = (Set<String>) getPackages.invoke(module);
            for (String pkg : packages) {
                implAddExportsOrOpens.invoke(module, pkg, unnamedModule, true, true);
            }
        }
    }

    @Override
    public boolean isDefault() {
        return Agent.active;
    }

    @Override
    public boolean isRequired() {
        return this.isDefault();
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 11;
    }
}
