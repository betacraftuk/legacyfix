package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.*;

public class MousePatch extends Patch {
    private boolean mouseDeltaFound = false;

    public MousePatch() {
        super("mouse", "Fixes mouse handling, required for deAWT", true);
    }

    @Override
    public void apply(final PatchPool patchPool) throws Exception {
        final String mouseHelperClassName = GameClasses.findMouseHelperClass(patchPool);
        if (mouseHelperClassName == null) {
            throw new PatchUnapplicableException("No MouseHelper found");
        }

        patchPool.addCtTransformer(mouseHelperClassName, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                transformMouseHelper(ctClass);
            }
        });

        final String minecraftClassName = GameClasses.findMinecraftClass(patchPool);
        if (minecraftClassName == null) {
            throw new PatchException("No Minecraft class found");
        }

        patchPool.addCtTransformer(minecraftClassName, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                transformMinecraft(ctClass);
            }
        });

        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) {
                if (mouseDeltaFound) {
                    return null;
                }

                if (name.startsWith("org.lwjgl") || name.equals(mouseHelperClassName)) {
                    return null;
                }

                CtClass clas = patchPool.getRawClass(name);
                if (clas == null) {
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
                                mouseDeltaFound = true;
                                m.replace("$_ = 0;");
                                Logger.debug("Patched Mouse.getDX()");
                            } else if ("org.lwjgl.input.Mouse".equals(m.getClassName()) &&
                                "getDY".equals(m.getMethodName()) &&
                                "()I".equalsIgnoreCase(m.getSignature())) {
                                mouseDeltaFound = true;
                                m.replace("$_ = 0;");
                                Logger.debug("Patched Mouse.getDY()");
                            }
                        }
                    });

                    if (mouseDeltaFound) {
                        return clas.toBytecode();
                    }
                } catch (Throwable t) {
                    Logger.error("mouse", t);
                }

                return null;
            }
        });
    }

    private void transformMouseHelper(CtClass mouseHelperClass) throws Exception {
        if (mouseHelperClass.isFrozen()) {
            mouseHelperClass.defrost();
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

        Logger.debug("MouseHelper AWT Robot: " + usesRobot);

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

        Logger.debug("Mouse Y invert: " + invert);

        if (mouseHelperMethods.length == 1) {
            Logger.debug("MouseHelper method size: 1");
            mouseHelperMethods[0].setBody((invert ? tickBodyInvert : tickBody));
        } else if (mouseHelperMethods.length == 2) {
            Logger.debug("MouseHelper method size: 2");
            mouseHelperMethods[0].setBody(lockBody);
            mouseHelperMethods[1].setBody((invert ? tickBodyInvert : tickBody));
        } else if (mouseHelperMethods.length >= 3) {
            Logger.debug("MouseHelper method size: " + mouseHelperMethods.length);
            mouseHelperMethods[0].setBody(lockBody);
            mouseHelperMethods[1].setBody("" +
                "{" +
                "    org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
                "    org.lwjgl.input.Mouse.setGrabbed(false);" +
                "}"
            );
            mouseHelperMethods[2].setBody((invert ? tickBodyInvert : tickBody));
        }
    }

    private void transformMinecraft(CtClass minecraftClass) throws Exception {
        if (minecraftClass.isFrozen()) {
            minecraftClass.defrost();
        }

        minecraftClass.instrument(new ExprEditor() {
            public void edit(MethodCall m) throws CannotCompileException {
                if ("org.lwjgl.input.Mouse".equals(m.getClassName())
                    && "getDX".equals(m.getMethodName())
                    && "()I".equalsIgnoreCase(m.getSignature())
                ) {
                    m.replace("$_ = 0;");
                    Logger.debug("Patched Mouse.getDX() in Minecraft");
                } else if ("org.lwjgl.input.Mouse".equals(m.getClassName())
                    && "getDY".equals(m.getMethodName())
                    && "()I".equalsIgnoreCase(m.getSignature())
                ) {
                    m.replace("$_ = 0;");
                    Logger.debug("Patched Mouse.getDY() in Minecraft");
                }
            }
        });

        minecraftClass.instrument(new ExprEditor() {
            public void edit(MethodCall mc) throws CannotCompileException {
                if (!(
                    "org.lwjgl.input.Mouse".equals(mc.getClassName())
                    && "setNativeCursor".equals(mc.getMethodName())
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
    }
}
