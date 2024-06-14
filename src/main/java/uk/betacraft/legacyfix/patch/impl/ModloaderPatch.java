package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

/**
 * Fixes Risugami's ModLoader on Java 9 or higher.
 */
public class ModloaderPatch extends Patch {
    public ModloaderPatch() {
        super("modloader", "ModLoader");
    }

    @Override
    public boolean apply(Instrumentation inst) throws Exception {
        if (LegacyFixAgent.getJvmVersion() >= 11) {
            String args = JvmUtils.getJvmArguments();
            if (!(
                args.contains("--add-opens=java.base/java.nio=ALL-UNNAMED") &&
                args.contains("--add-opens=java.base/java.net=ALL-UNNAMED") &&
                args.contains("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED") &&
                args.contains("--add-opens=java.base/java.lang=ALL-UNNAMED") &&
                args.contains("--add-opens=java.base/java.util=ALL-UNNAMED") &&
                args.contains("--add-opens=java.desktop/java.awt=ALL-UNNAMED") &&
                args.contains("--add-opens=java.base/sun.net.www.protocol.http=ALL-UNNAMED") &&
                args.contains("-Djava.system.class.loader=uk.betacraft.legacyfix.fix.URLClassLoaderBridge")
            )) {
                LFLogger.error(
                    "The ModLoader patch couldn't be applied. Note that this fix requires legacyfix to be in the classpath along with specific JVM arguments:",
                    "--add-opens=java.base/java.nio=ALL-UNNAMED " +
                    "--add-opens=java.base/java.net=ALL-UNNAMED " +
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED " +
                    "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                    "--add-opens=java.base/java.util=ALL-UNNAMED " +
                    "--add-opens=java.desktop/java.awt=ALL-UNNAMED " +
                    "--add-opens=java.base/sun.net.www.protocol.http=ALL-UNNAMED " +
                    "-Djava.system.class.loader=uk.betacraft.legacyfix.patch.URLClassLoaderBridge"
                );

                return false;
            }
        }

        CtClass clazz = pool.get("java.lang.Class");
        CtClass string = pool.get("java.lang.String");
        CtMethod method = clazz.getDeclaredMethod("getDeclaredField", new CtClass[] {string});

        method.setBody(
            "{" +
            "	java.lang.reflect.Field[] fieldz = getDeclaredFields0(false);" +
            "	for (int i = 0; i < fieldz.length; i++) {" +
            "		java.lang.reflect.Field one = fieldz[i];" +
            "		if ($1.equals(one.getName())) {" +
            "			return one;" +
            "		}" +
            "	}" +
            "	return null;" +
            "}"
        );

        method = clazz.getDeclaredMethod("getDeclaredFields");
        method.setBody(
            "{" +
            "	return copyFields($0.getDeclaredFields0(false));" +
            "}"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(clazz.getName()), clazz.toBytecode()));

        clazz = pool.get("java.lang.ClassLoader");
        method = clazz.getDeclaredMethod("loadClass", new CtClass[] {string});
        method.insertBefore(
        "if ($1.startsWith(\"\\.mod_\")) {" +
            "	$1 = $1.substring(1);" +
            "}"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(clazz.getName()), clazz.toBytecode()));
        return true;
    }

    @Override
    public boolean shouldApply() {
        return super.shouldApply() && LegacyFixAgent.getJvmVersion() >= 9;
    }
}
