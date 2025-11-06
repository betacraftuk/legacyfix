package uk.betacraft.legacyfix.patch.impl.java;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

public class ModloaderPatch extends Patch {
    private static final String[] vmArgs = {
        "-Djava.system.class.loader=uk.betacraft.legacyfix.patch.URLClassLoaderBridge",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.base/sun.net.www.protocol.http=ALL-UNNAMED",
        "--add-opens=java.base/sun.net.www.protocol.https=ALL-UNNAMED",
    };

    public ModloaderPatch() {
        super("modloader", "Allows Risugami's ModLoader to work on Java 9+", true, true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        String args = JvmUtils.getJvmArguments();
        if (JvmUtils.getJvmVersion() >= 11) {
            for (String vmArg : vmArgs) {
                if (args.contains(vmArg)) continue;

                StringBuilder joinedArgs = new StringBuilder();
                for (String arg : vmArgs) {
                    joinedArgs.append(arg);
                    if (joinedArgs.length() != 1) {
                        joinedArgs.append(" ");
                    }
                }

                Logger.error(
                    "The ModLoader patch couldn't be applied because required JVM arguments are missing.",
                    "Please add the following arguments to your JVM options and try again:",
                    "", joinedArgs.toString(), "",
                    "NOTE: You will need to add legacyfix into the classpath (add to minecraft.jar)."
                );
                throw new PatchException("Conditions not met");
            }
        }

        CtClass clazz = patchPool.getClass("java.lang.Class");
        CtMethod method = clazz.getDeclaredMethod("getDeclaredField", new CtClass[] {CT_STRING});

        method.setBody("" +
            "{" +
            "    java.lang.reflect.Field[] fieldz = getDeclaredFields0(false);" +
            "    for (int i = 0; i < fieldz.length; i++) {" +
            "        java.lang.reflect.Field one = fieldz[i];" +
            "        if ($1.equals(one.getName())) {" +
            "            return one;" +
            "        }" +
            "    }" +
            "    return null;" +
            "}"
        );

        method = clazz.getDeclaredMethod("getDeclaredFields");
        method.setBody("" +
            "{" +
            "    return copyFields($0.getDeclaredFields0(false));" +
            "}"
        );

        patchPool.patchClass(clazz);

        clazz = patchPool.getClass("java.lang.ClassLoader");
        method = clazz.getDeclaredMethod("loadClass", new CtClass[] {CT_STRING});
        method.insertBefore("" +
            "if ($1.startsWith(\"\\.mod_\")) {" +
            "    $1 = $1.substring(1);" +
            "}"
        );

        patchPool.patchClass(clazz);
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9 && patchPool.getClass("BaseMod") != null;
    }
}
