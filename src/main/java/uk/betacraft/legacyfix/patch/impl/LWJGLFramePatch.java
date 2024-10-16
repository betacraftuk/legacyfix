package uk.betacraft.legacyfix.patch.impl;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.LegacyFixLauncher;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;
import uk.betacraft.legacyfix.util.IconUtils;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class LWJGLFramePatch extends Patch {

    public LWJGLFramePatch() {
        super("lwjglframepatch", "Patches LWJGL Frame for title and resolution", true);
    }

    @Override
    public void apply(final Instrumentation inst) throws PatchException, Exception {
        try {
            IconUtils.loadIcons((String) LegacyFixAgent.getSettings().get("lf.icon"));
        } catch (Exception e) {
            LFLogger.error(this, e);
        }

        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");
        if (displayClass.isFrozen())
            displayClass.defrost();

        CtMethod setTitleMethod = displayClass.getDeclaredMethod("setTitle", new CtClass[]{PatchHelper.stringClass});

        // On init
        // @formatter:off
        setTitleMethod.insertBefore(
            // Title
            "Class legacyfix = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
            "$1 = (String) legacyfix.getMethod(\"getFrameName\", null).invoke(null, null);" +

            // Resizable
            "org.lwjgl.opengl.Display.setResizable(true);" +

            // 16x16 icon
            "java.lang.reflect.Field f16 = java.lang.ClassLoader.getSystemClassLoader()" +
            "   .loadClass(\"uk.betacraft.legacyfix.util.IconUtils\").getDeclaredField(\"pixels16\");" +
            "f16.setAccessible(true);" +
            "java.nio.ByteBuffer pix16 = f16.get(null);" +

            // 32x32 icon
            "java.lang.reflect.Field f32 = java.lang.ClassLoader.getSystemClassLoader()" +
            "   .loadClass(\"uk.betacraft.legacyfix.util.IconUtils\").getDeclaredField(\"pixels32\");" +
            "f32.setAccessible(true);" +
            "java.nio.ByteBuffer pix32 = f32.get(null);" +

            // Setting the icon
            "org.lwjgl.opengl.Display.setIcon(new java.nio.ByteBuffer[] {pix16, pix32});"
        );

        CtClass displayModeClass = pool.get("org.lwjgl.opengl.DisplayMode");

        CtConstructor displayModeConstructor = displayModeClass.getDeclaredConstructor(
                new CtClass[]{PatchHelper.intClass, PatchHelper.intClass});

        // @formatter:off
        displayModeConstructor.insertBefore(
            "Class legacyfix = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
            "$1 = ((Integer) legacyfix.getMethod(\"getWidth\", null).invoke(null, null)).intValue();" +
            "$2 = ((Integer) legacyfix.getMethod(\"getHeight\", null).invoke(null, null)).intValue();"
        );
        // @formatter:on

        inst.redefineClasses(new ClassDefinition(Class.forName(displayModeClass.getName()), displayModeClass.toBytecode()));

        // Make 13w16a-13w24b honor custom width & height
        CtClass minecraftMainClass = pool.getOrNull(LegacyFixLauncher.getValue("mainClass", "net.minecraft.client.main.Main"));
        if (minecraftMainClass == null)
            return;

        CtMethod mainMethod = minecraftMainClass.getDeclaredMethod("main");

        mainMethod.instrument(new ExprEditor() {
            public void edit(NewExpr m) {
                try {
                    CtConstructor minecraftConstructor = m.getConstructor();
                    CtClass[] parameterTypes = minecraftConstructor.getParameterTypes();

                    boolean isMinecraft = false;
                    for (CtClass proxyClass : parameterTypes) {
                        // Minecraft class constructor in affected versions takes a Proxy object
                        if (!"java.net.Proxy".equals(proxyClass.getName())) {
                            continue;
                        }

                        isMinecraft = true;
                    }

                    if (!isMinecraft)
                        return;

                    for (int i = 0; i < parameterTypes.length; i++) {
                        CtClass intClass = parameterTypes[i];
                        if (!"int".equals(intClass.getName())) {
                            continue;
                        }

                        // @formatter:off
                        minecraftConstructor.insertBefore(
                            "Class legacyfix = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
                            "$" + (i + 1) + " = ((Integer) legacyfix.getMethod(\"getWidth\", null).invoke(null, null)).intValue();" +
                            "$" + (i + 2) + " = ((Integer) legacyfix.getMethod(\"getHeight\", null).invoke(null, null)).intValue();"
                        );
                        // @formatter:on

                        CtClass minecraftClass = minecraftConstructor.getDeclaringClass();
                        inst.redefineClasses(new ClassDefinition(Class.forName(minecraftClass.getName()), minecraftClass.toBytecode()));
                        break;
                    }
                } catch (Throwable t) {
                    LFLogger.error("lwjglframepatch", t);
                }
            }
        });
    }
}
