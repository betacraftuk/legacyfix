package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.util.OSUtils;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class RawInputPatch extends Patch {
    public RawInputPatch() {
        super("rawinput", "Enables raw input on Windows", false);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        if (OSUtils.getOS() != OSUtils.OS.WINDOWS) {
            return;
        }

        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");
        if (displayClass.isFrozen()) { 
            displayClass.defrost();
        }
        CtClass mouseClass = pool.get("org.lwjgl.input.Mouse");
        if (mouseClass.isFrozen()) {
            mouseClass.defrost();
        }

        CtMethod create = displayClass.getDeclaredMethod("create", new CtClass[]{});
        create.insertAfter(
            "{" +
            "  Class nativeBridge = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.INativeBridge\");" +
            "  Object instance = nativeBridge.getField(\"INSTANCE\").get(null);" +
            "  java.lang.reflect.Method method = nativeBridge.getMethod(\"InstallRawInputHook\", new Class[0]);" +
            "  method.invoke(instance, (Object[]) null);" +
            "}"
        );

        CtMethod getDX = mouseClass.getDeclaredMethod("getDX", new CtClass[]{});
        getDX.insertBefore(
            "{" +
            "  Class nativeBridge = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.INativeBridge\");" +
            "  Object instance = nativeBridge.getField(\"INSTANCE\").get(null);" +
            "  java.lang.reflect.Method availableMethod = nativeBridge.getMethod(\"BIsWndProcHooked\", new Class[0]);" +
            "  if (((Boolean) availableMethod.invoke(instance, (Object[]) null)).booleanValue()) {" +
            "    java.lang.reflect.Method method = nativeBridge.getMethod(\"GetRawDeltaX\", new Class[0]);" +
            "    return ((Integer) method.invoke(instance, (Object[]) null)).intValue();" +
            "  }" +
            "}"
        );
        CtMethod getDY = mouseClass.getDeclaredMethod("getDY", new CtClass[]{});
        getDY.insertBefore(
            "{" +
            "  Class nativeBridge = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.INativeBridge\");" +
            "  Object instance = nativeBridge.getField(\"INSTANCE\").get(null);" +
            "  java.lang.reflect.Method availableMethod = nativeBridge.getMethod(\"BIsWndProcHooked\", new Class[0]);" +
            "  if (((Boolean) availableMethod.invoke(instance, (Object[]) null)).booleanValue()) {" +
            "    java.lang.reflect.Method method = nativeBridge.getMethod(\"GetRawDeltaY\", new Class[0]);" +
            "    return ((Integer) method.invoke(instance, (Object[]) null)).intValue();" +
            "  }" +
            "}"
        );

        inst.redefineClasses(
            new ClassDefinition(Class.forName(displayClass.getName()), displayClass.toBytecode()),
            new ClassDefinition(Class.forName(mouseClass.getName()), mouseClass.toBytecode())
        );
    }
}