package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;

import java.lang.instrument.Instrumentation;

public class VisualizerPatch extends Patch {
    public VisualizerPatch() {
        super("visualizer", "Patches the main class to run the map visualizer", false, false);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        final String mainClassName = System.getProperty("sun.java.command");
        CtClass mainClass = pool.getOrNull(mainClassName);

        if (mainClass == null) {
            throw new PatchException("No main class found for name " + mainClassName);
        }

        LFLogger.debug("Hooking visualizer into " + mainClassName);
        if (mainClass.isFrozen()) {
            mainClass.defrost();
        }

        CtMethod mainMethod = null;
        for (CtMethod method : mainClass.getDeclaredMethods()) {
            if ("main".equals(method.getName()) && "([Ljava/lang/String;)V".equals(method.getSignature())) {
                mainMethod = method;
                break;
            }
        }

        if (mainMethod == null) {
            throw new PatchException("No main method found for class " + mainClassName);
        }

        mainMethod.setBody("" +
            "{" +
            "    Class launcherClass = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.VisualizerLauncher\");" +
            "    java.lang.reflect.Method method = launcherClass.getMethod(\"launchPreviewApplet\", new Class[] {String.class});" +
            "    boolean retval = ((Boolean) method.invoke(null, new Object[] {\"net.minecraft.isom.IsomPreviewApplet\"})).booleanValue();" +
            "    if (!retval) {" +
            "       System.out.println(\"Couldn't launch Visualizer IsomPreviewApplet. Aborting!\");" +
            "        return;" +
            "   }" +
            "}"
        );

        this.redefineClass(inst, mainClass);
    }
}