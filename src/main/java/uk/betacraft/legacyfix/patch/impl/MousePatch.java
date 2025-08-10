package uk.betacraft.legacyfix.patch.impl;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchHelper;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

/**
 * Fixes mouse on modern macOS, required for deAWT
 */
public class MousePatch extends Patch {
    private boolean mouseDXYmatched;

    public MousePatch() {
        super("mouse", "Fixes mouse handling, required for deAWT", true);
    }

    @Override
    public void apply(final Instrumentation inst) throws Exception {
        final CtClass mouseHelperClass = PatchHelper.findMouseHelperClass(pool);

        // @formatter:off
        if (mouseHelperClass != null) {
            String[] deltaXYFieldNames = new String[2];
            boolean usesRobot = false;
            for (CtField field : mouseHelperClass.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers()) && field.getType().getName().equals("int")) {
                    deltaXYFieldNames[deltaXYFieldNames[0] == null ? 0 : 1] = field.getName();
                } else if (field.getType().getName().equals("java.awt.Robot")) {
                    usesRobot = true;
                }
            }

            LFLogger.debug("mouse", "MouseHelper uses AWT Robot: " + usesRobot);

            CtMethod[] mouseHelperMethods = mouseHelperClass.getDeclaredMethods();
            String lockBody = (
                "{" +
                "    org.lwjgl.input.Mouse.setGrabbed(true);" +
                "    $0." + deltaXYFieldNames[0] + " = 0;" +
                "    $0." + deltaXYFieldNames[1] + " = 0;" +
                "}"
            );

            String tickBody = (
                "{" +
                "    $0." + deltaXYFieldNames[0] + " = org.lwjgl.input.Mouse.getDX();" +
                "    $0." + deltaXYFieldNames[1] + " = org.lwjgl.input.Mouse.getDY();" +
                "}"
            );

            String tickBodyInvert = (
                "{" +
                "    $0." + deltaXYFieldNames[0] + " = org.lwjgl.input.Mouse.getDX();" +
                "    $0." + deltaXYFieldNames[1] + " = -(org.lwjgl.input.Mouse.getDY());" +
                "}"
            );

            boolean invert = "invert".equals(LegacyFixAgent.getSetting("lf.mouse", null));
            if (usesRobot) invert = !invert;

            LFLogger.debug("mouse", "Mouse Y invert: " + invert);

            if (mouseHelperMethods.length == 1) {
                LFLogger.debug("mouse", "Mouse Helper method size: 1");
                mouseHelperMethods[0].setBody((invert ? tickBodyInvert : tickBody));
            } else if (mouseHelperMethods.length == 2) {
                LFLogger.debug("mouse", "Mouse Helper method size: 2");
                mouseHelperMethods[0].setBody(lockBody);
                mouseHelperMethods[1].setBody((invert ? tickBodyInvert : tickBody));
            } else if (mouseHelperMethods.length >= 3) {
                LFLogger.debug("mouse", "Mouse Helper method size: " + mouseHelperMethods.length);
                mouseHelperMethods[0].setBody(lockBody);
                // unlock
                mouseHelperMethods[1].setBody(
                    "{" +
                    "    org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
                    "    org.lwjgl.input.Mouse.setGrabbed(false);" +
                    "}"
                );
                mouseHelperMethods[2].setBody((invert ? tickBodyInvert : tickBody));
            }

            this.redefineClass(inst, mouseHelperClass);

            // @formatter:on

            // Replace all calls to Mouse.getDX() and Mouse.getDY() with 0
            inst.addTransformer(new ClassFileTransformer() {
                public byte[] transform(ClassLoader loader, String className, Class<?> classRedefined, ProtectionDomain domain, byte[] classfileBuffer) {
                    CtClass clas = pool.getOrNull(className.replace("/", "."));
                    if (clas == null || clas.getName().startsWith("org.lwjgl") || clas.getName().equals(mouseHelperClass.getName())) {
                        return null;
                    }

                    try {
                        if (clas.isFrozen()) {
                            clas.defrost();
                        }

                        clas.instrument(new ExprEditor() {
                            public void edit(MethodCall m) throws CannotCompileException {
                                if ("org.lwjgl.input.Mouse".equals(m.getClassName()) &&
                                    "getDX".equals(m.getMethodName()) &&
                                    "()I".equalsIgnoreCase(m.getSignature())) {
                                    mouseDXYmatched = true;
                                    m.replace("$_ = 0;");
                                    LFLogger.debug("mouse", "Mouse.getDX() match!");

                                } else if ("org.lwjgl.input.Mouse".equals(m.getClassName()) &&
                                    "getDY".equals(m.getMethodName()) &&
                                    "()I".equalsIgnoreCase(m.getSignature())) {
                                    mouseDXYmatched = true;
                                    m.replace("$_ = 0;");
                                    LFLogger.debug("mouse", "Mouse.getDY() match!");
                                }
                            }
                        });

                        if (mouseDXYmatched) {
                            mouseDXYmatched = false;
                            inst.removeTransformer(this); // job is done, don't fire any more classes

                            return clas.toBytecode();
                        }
                    } catch (Throwable t) {
                        LFLogger.error("mouse", t);
                    }

                    return null;
                }
            });
        }

        // Some versions refer to setNativeCursor within methods of the Minecraft class,
        // we need to account for that too
        CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
        if (minecraftClass.isFrozen()) {
            minecraftClass.defrost();
        }

        minecraftClass.instrument(new ExprEditor() {
            public void edit(MethodCall mc) throws CannotCompileException {
                if ("org.lwjgl.input.Mouse".equals(mc.getClassName()) && "setNativeCursor".equals(mc.getMethodName())) {
                    // @formatter:off
                    mc.replace(
                        "{" +
                        "    if ($1 == null) {" +
                        "        org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
                        "    }" +
                        "    org.lwjgl.input.Mouse.setGrabbed($1 != null);" +
                        "    $_ = $proceed($$);" +
                        "}"
                    );
                    // @formatter:on
                }
            }
        });

        this.redefineClass(inst, minecraftClass);
    }
}
