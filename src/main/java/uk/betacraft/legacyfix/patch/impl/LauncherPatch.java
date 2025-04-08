package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class LauncherPatch extends Patch {
    public LauncherPatch() {
        super("launcher", "Patches the main launcher class, instead of having a custom one", true, true);
    }

    public void apply(Instrumentation inst) throws Exception {
        String mainClass = System.getProperty("sun.java.command");
        LFLogger.debug("Main class: ", mainClass);

        if (mainClass == null) {
            throw new PatchException("Main class not found");
        }

        if (mainClass.equals("org.prismlauncher.EntryPoint")) {
            LFLogger.info("Prism Launcher detected, patching!");
            patchPrism(inst);
        }
    }

    private void patchPrism(Instrumentation inst) throws Exception {
        CtClass parametersClass = pool.getOrNull("org.prismlauncher.utils.Parameters");
        if (parametersClass == null) {
            throw new PatchException("Parameters class not found?");
        }

        if (parametersClass.isFrozen()) {
            parametersClass.defrost();
        }

        CtMethod getStringDefault = parametersClass.getDeclaredMethod(
                "getString",
                pool.get(new String[] { "java.lang.String", "java.lang.String" })
        );

        CtMethod getList = parametersClass.getDeclaredMethod(
                "getList",
                pool.get(new String[] { "java.lang.String", "java.util.List" })
        );

        //@formatter:off
        getStringDefault.insertBefore(
            "if ($1.equals(\"mainClass\")) { " +
            "    return \"uk.betacraft.legacyfix.LegacyFixLauncher\"; " +
            "}"
        );

        getList.insertAfter(
            "if ($1.equals(\"traits\")) {" +
            "    $_ = new java.util.ArrayList();" +
            "    $_.add(\"noapplet\");" +
            "}"
        );
        //@formatter:on

        inst.redefineClasses(new ClassDefinition(Class.forName(parametersClass.getName()), parametersClass.toBytecode()));
    }
}