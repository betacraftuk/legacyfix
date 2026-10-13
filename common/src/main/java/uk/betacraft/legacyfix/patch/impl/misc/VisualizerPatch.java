package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class VisualizerPatch extends Patch {
    public VisualizerPatch() {
        super("visualizer", "Patches the main class to run the Infdev map visualizer", false);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        final String mainClassName = System.getProperty("sun.java.command").split(" ")[0];

        patchPool.addCtTransformer(mainClassName, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                Logger.debug("Hooking visualizer into " + mainClassName);

                CtMethod mainMethod = null;
                for (CtMethod method : ctClass.getDeclaredMethods()) {
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
                    "    boolean success = ((Boolean) method.invoke(null, new Object[] {\"net.minecraft.isom.IsomPreviewApplet\"})).booleanValue();" +
                    "    if (!success) {" +
                    "        System.out.println(\"Couldn't launch Visualizer IsomPreviewApplet. Aborting!\");" +
                    "        return;" +
                    "    }" +
                    "}"
                );
            }
        });
    }
}