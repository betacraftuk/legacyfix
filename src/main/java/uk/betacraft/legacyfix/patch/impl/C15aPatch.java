package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

/**
 * Fixes server joining for the first c0.0.15a
 */
public class C15aPatch extends Patch {

    public C15aPatch() {
        super("c0.0.15a", "Patches c0.0.15a to not join Notchian server", false);
    }

    @Override
    public void apply(Instrumentation inst) throws PatchException, Exception {
        // Join server method
        CtClass minecraftClass = pool.get("com.mojang.minecraft.c");

        CtMethod setServerMethod = minecraftClass.getDeclaredMethod("a", new CtClass[]{PatchHelper.stringClass, PatchHelper.intClass});

        String server = System.getProperty("server", null);
        String port = System.getProperty("port", "25565");

        if (server == null) {
            // If no server is being joined, the game should start in singleplayer mode
            setServerMethod.setBody("{}");
        } else {
            // Block calls to the dead Notchian server and use our address instead
            // @formatter:off
            setServerMethod.setBody(
                "{" +
                "    if ($1.equals(\"79.136.77.240\") && $2 == 5565) {" +
                "        return;" +
                "    }" +
                "    try {" +
                "        $0.C = new com.mojang.minecraft.net.b(this, \"" + server + "\", " + port + ", $0.e.a);" +
                "    } catch (Throwable t) { t.printStackTrace(); }" +
                "}"
            );
        }

        // Make the server join method run after the game is fully initialized
        // It will call an empty method if no server arguments were given for legacyfix (watch above)
        CtMethod runmeth = minecraftClass.getDeclaredMethod("run");
        runmeth.insertBefore(
                "$0.a(\"nothing\", 420);"
        );

        inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(minecraftClass.getName()), minecraftClass.toBytecode())});
    }
}
