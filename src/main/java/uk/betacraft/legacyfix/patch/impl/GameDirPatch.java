package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtConstructor;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class GameDirPatch extends Patch {
    public GameDirPatch() {
        super("gamedirpatch", "Redirects Minecraft to the intended game directory", true);
    }

    public void apply(Instrumentation inst) throws PatchException, Exception {
        CtClass fileClass = pool.get("java.io.File");

        CtConstructor fileConstructor = fileClass.getDeclaredConstructor(
                new CtClass[] {PatchHelper.stringClass, PatchHelper.stringClass});

        // @formatter:off
        fileConstructor.insertBefore(
            "if ($1.equals(System.getenv(\"APPDATA\")) || $1.equals(System.getProperty(\"user.home\"))) {" +
            "   if ($2.equals(\".minecraft/\") || $2.equals(\"minecraft/\") || " +
            "               $2.equals(\"Library/Application Support/minecraft\")) {" +
            "       $1 = null;" +
            "       $2 = \"" + LegacyFixAgent.getGameDir() + "\";" +
            "   }" +
            "}"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(fileClass.getName()), fileClass.toBytecode()));
    }
}
