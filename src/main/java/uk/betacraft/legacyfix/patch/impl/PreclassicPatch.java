package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class PreclassicPatch extends Patch {

    public PreclassicPatch() {
        super("preclassicpatch", "Patches for pre-Classic", true);
    }

    @Override
    public void apply(Instrumentation inst) throws PatchException, Exception {
        CtClass displayModeClass = pool.get("org.lwjgl.opengl.DisplayMode");

        String frameName = LegacyFixAgent.getSetting("lf.frameName", "Minecraft pre-Classic");
        String width = LegacyFixAgent.getSetting("lf.width", "854");
        String height = LegacyFixAgent.getSetting("lf.height", "480");

        CtConstructor displayModeConstructor = displayModeClass.getDeclaredConstructor(
                new CtClass[]{PatchHelper.intClass, PatchHelper.intClass});

        // @formatter:off
        displayModeConstructor.insertBefore(
            "if ($1 == 1024 && $2 == 768) {" +
            "    $1 = " + width + ";" +
            "    $2 = " + height + ";" +
            "}"
        );
        // @formatter:on

        inst.redefineClasses(new ClassDefinition(Class.forName(displayModeClass.getName()), displayModeClass.toBytecode()));

        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");
        CtMethod setTitleMethod = displayClass.getDeclaredMethod("setTitle", new CtClass[]{PatchHelper.stringClass});

        // @formatter:off
        setTitleMethod.insertBefore(
            "$1 = \"" + frameName + "\";"
        );
        // @formatter:on

        inst.redefineClasses(new ClassDefinition(Class.forName(displayClass.getName()), displayClass.toBytecode()));
    }

    @Override
    public boolean shouldApply() {
        return super.shouldApply() && (pool.getOrNull("com.mojang.minecraft.RubyDung") != null || pool.getOrNull("com.mojang.rubydung.RubyDung") != null);
    }
}
