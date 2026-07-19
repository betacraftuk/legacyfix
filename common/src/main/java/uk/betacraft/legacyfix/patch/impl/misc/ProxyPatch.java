package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class ProxyPatch extends Patch {
    public ProxyPatch() {
        super("proxy", "", true, false);
    }

    public void apply(PatchPool patchPool) throws Exception {
        String version = Agent.getSetting("lf.version", null);
        if (version == null) {
            String minecraftClass = GameClasses.findMinecraftClass(patchPool);
            if (minecraftClass != null) {
                patchPool.addCtTransformer(minecraftClass, new CtTransformer() {
                    public void transform(CtClass ctClass) throws Exception {
                        for (CtMethod method : ctClass.getDeclaredMethods()) {
                            if (!"()V".equals(method.getMethodInfo().getDescriptor())) {
                                continue;
                            }

                            method.instrument(new ExprEditor() {
                                @Override
                                public void edit(MethodCall m) throws CannotCompileException {
                                    if ("org.lwjgl.opengl.Display".equals(m.getClassName())
                                        && "setTitle".equals(m.getMethodName())
                                    ) {
                                        m.replace("" +
                                            "org.lwjgl.opengl.Display.setTitle($1);" +
                                            "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                                            "gameArgsClass.getMethod(\"setVersion\", new Class[]{String.class}).invoke(null, new Object[]{ $1 });"
                                        );
                                    }
                                }
                            });
                        }
                    }
                });
            } else {
                Logger.error("ProxyPatch", "Couldn't find the main game class! Please specify your game version with the -Dlf.version argument.");
            }
        }

        // Set by tweakers before patches are applied
        if (GameArgs.initialized()) {
            return;
        }

        // TODO: Prism Launcher detection
        //  "org.prismlauncher.launch.mainclass" isn't yet set here
        if (System.getProperty("sun.java.command") != null && System.getProperty("sun.java.command").contains("uk.betacraft.legacyfix.applet.AppletLauncher")) {
            return;
        }

        if (findArgsWithApplet(patchPool)) {
            return;
        }

        if (findArgsByMain(patchPool)) {
            return;
        }

        if (findArgsWithWrapper(patchPool)) {
            return;
        }

        Logger.error(
            "",
            "Game arguments could not be found and the proxy might not work properly.",
            "If you experience issues, report this to the LegacyFix GitHub along with info about your game setup.",
            ""
        );
    }

    private boolean findArgsWithApplet(PatchPool patchPool) throws Exception {
        String minecraftClass = GameClasses.findMinecraftClass(patchPool);
        String appletClass = GameClasses.findMinecraftAppletClass(patchPool);

        if (minecraftClass == null || appletClass == null) {
            return false;
        }

        final String[] appletFieldName = { null };
        {
            CtClass mc = patchPool.getRawClass(minecraftClass);

            for (CtField field : mc.getDeclaredFields()) {
                if (appletClass.equals(field.getType().getName())) {
                    appletFieldName[0] = field.getName();
                    Logger.debug("Found applet field: " + appletFieldName[0]);
                    break;
                }
            }
        }

        if (appletFieldName[0] == null) {
            return false;
        }

        patchPool.addCtTransformer(minecraftClass, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod mainMethod = null;
                for (CtMethod method : ctClass.getDeclaredMethods()) {
                    if (method.getName().equals("main")) {
                        mainMethod = method;
                    }
                }
                if (mainMethod == null) {
                    return;
                }

                mainMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "boolean initialized = ((java.lang.Boolean) gameArgsClass.getMethod(\"initialized\", null).invoke(null, null)).booleanValue();" +
                    "if (!initialized) {" +
                    "   gameArgsClass.getMethod(\"setArgsRaw\", new Class[]{String[].class}).invoke(null, new Object[]{$1});" +
                    "}"
                );

                CtMethod runMethod = ctClass.getDeclaredMethod("run");
                runMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "boolean initialized = ((java.lang.Boolean) gameArgsClass.getMethod(\"initialized\", null).invoke(null, null)).booleanValue();" +
                    "java.applet.Applet applet = (java.applet.Applet) $0." + appletFieldName[0] + ";" +
                    "if (!initialized && (applet != null)) {" +
                    "   gameArgsClass.getMethod(\"setArgs\", new Class[]{String.class, String.class}).invoke(null, new Object[]{" +
                    "       applet.getParameter(\"username\")," +
                    "       applet.getParameter(\"sessionid\")" +
                    "   });" +
                    "}"
                );
            }
        });

        return true;
    }

    private boolean findArgsByMain(PatchPool patchPool) throws Exception {
        if (!Agent.active) {
            return false;
        }

        CtClass mainClass = patchPool.getRawClass("net.minecraft.client.main.Main");
        if (mainClass == null) {
            return false;
        }

        patchPool.addCtTransformer("net.minecraft.client.main.Main", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod mainMethod = ctClass.getDeclaredMethod("main");
                mainMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "gameArgsClass.getMethod(\"setArgsRaw\", new Class[]{String[].class}).invoke(null, new Object[]{$1});"
                );
            }
        });

        return true;
    }

    private boolean findArgsWithWrapper(PatchPool patchPool) throws Exception {
        if (patchPool.getRawClass("net.minecraft.Launcher") == null) {
            return false;
        }

        patchPool.addCtTransformer("net.minecraft.Launcher", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod mainMethod = ctClass.getDeclaredMethod("setParameter");
                mainMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "gameArgsClass.getMethod(\"setParam\", new Class[]{String.class, String.class}).invoke(null, new Object[]{$1, $2});"
                );
            }
        });

        return true;
    }
}
