package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;

public class MousePatch extends Patch {
    private boolean mouseDXYmatched;

    public MousePatch() {
        super("mouse", "Fixes mouse handling, required for deAWT", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        final CtClass mouseHelperClass = GameClasses.findMouseHelperClass(patchPool);
        if (mouseHelperClass == null) {
            return;
        }

        String[] deltaXYFieldNames = new String[2];
        boolean usesRobot = false;
        for (CtField field : mouseHelperClass.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && field.getType().getName().equals("int")) {
                deltaXYFieldNames[deltaXYFieldNames[0] == null ? 0 : 1] = field.getName();
            } else if (field.getType().getName().equals("java.awt.Robot")) {
                usesRobot = true;
            }
        }

        Logger.debug("mouse", "MouseHelper uses AWT Robot: " + usesRobot);

        CtMethod[] mouseHelperMethods = mouseHelperClass.getDeclaredMethods();
        String lockBody = ("" +
            "{" +
            "    org.lwjgl.input.Mouse.setGrabbed(true);" +
            "    $0." + deltaXYFieldNames[0] + " = 0;" +
            "    $0." + deltaXYFieldNames[1] + " = 0;" +
            "}"
        );

        String tickBody = ("" +
            "{" +
            "    $0." + deltaXYFieldNames[0] + " = org.lwjgl.input.Mouse.getDX();" +
            "    $0." + deltaXYFieldNames[1] + " = org.lwjgl.input.Mouse.getDY();" +
            "}"
        );

        String tickBodyInvert = ("" +
            "{" +
            "    $0." + deltaXYFieldNames[0] + " = org.lwjgl.input.Mouse.getDX();" +
            "    $0." + deltaXYFieldNames[1] + " = -(org.lwjgl.input.Mouse.getDY());" +
            "}"
        );

        boolean invert = "invert".equals(Agent.getSetting("lf.mouse", null));
        if (usesRobot) {
            invert = !invert;
        }

        Logger.debug("mouse", "Mouse Y invert: " + invert);

        if (mouseHelperMethods.length == 1) {
            Logger.debug("mouse", "Mouse Helper method size: 1");
            mouseHelperMethods[0].setBody((invert ? tickBodyInvert : tickBody));
        } else if (mouseHelperMethods.length == 2) {
            Logger.debug("mouse", "Mouse Helper method size: 2");
            mouseHelperMethods[0].setBody(lockBody);
            mouseHelperMethods[1].setBody((invert ? tickBodyInvert : tickBody));
        } else if (mouseHelperMethods.length >= 3) {
            Logger.debug("mouse", "Mouse Helper method size: " + mouseHelperMethods.length);
            mouseHelperMethods[0].setBody(lockBody);
            // unlock
            mouseHelperMethods[1].setBody("" +
                "{" +
                "    org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
                "    org.lwjgl.input.Mouse.setGrabbed(false);" +
                "}"
            );
            mouseHelperMethods[2].setBody((invert ? tickBodyInvert : tickBody));
        }

        patchPool.patchClass(mouseHelperClass);

        // Replace all calls to Mouse.getDX() and Mouse.getDY() with 0
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                CtClass clas = ctFromBytes(bytecode);
                if (clas == null || clas.getName().startsWith("org.lwjgl") || clas.getName().equals(mouseHelperClass.getName())) {
                    return null;
                }

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
                            Logger.debug("mouse", "Mouse.getDX() match!");

                        } else if ("org.lwjgl.input.Mouse".equals(m.getClassName()) &&
                            "getDY".equals(m.getMethodName()) &&
                            "()I".equalsIgnoreCase(m.getSignature())) {
                            mouseDXYmatched = true;
                            m.replace("$_ = 0;");
                            Logger.debug("mouse", "Mouse.getDY() match!");
                        }
                    }
                });

                if (mouseDXYmatched) {
                    mouseDXYmatched = false;
                    return clas.toBytecode();
                }

                return null;
            }
        });

        // Some versions refer to setNativeCursor within methods of the Minecraft class,
        // we need to account for that too
        CtClass minecraftClass = GameClasses.findMinecraftClass(patchPool);
        if (minecraftClass.isFrozen()) {
            minecraftClass.defrost();
        }

        minecraftClass.instrument(new ExprEditor() {
            public void edit(MethodCall mc) throws CannotCompileException {
                if (!(
                    "org.lwjgl.input.Mouse".equals(mc.getClassName()) &&
                    "setNativeCursor".equals(mc.getMethodName())
                )) return;

                mc.replace("" +
                    "{" +
                    "    if ($1 == null) {" +
                    "        org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
                    "    }" +
                    "    org.lwjgl.input.Mouse.setGrabbed($1 != null);" +
                    "    $_ = $proceed($$);" +
                    "}"
                );
            }
        });

        patchPool.patchClass(minecraftClass);
    }
}
