package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

/**
 * Fixes mouse on modern macOS, required for deAWT
 */
public class MousePatch extends Patch {
    private boolean mouseDXYmatched;

    public MousePatch() {
        super("mouse", "Fixes mouse on modern macOS, also required for deAWT", true);
    }

    @Override
    public void apply(final Instrumentation inst) throws PatchException, Exception {

        final CtClass mouseHelperClass = PatchHelper.findMouseHelperClass(pool);

        if (mouseHelperClass != null) {

            CtMethod[] mouseHelperMethods = mouseHelperClass.getDeclaredMethods();
            mouseHelperMethods[0].setBody(
                    "{" +
                            "    org.lwjgl.input.Mouse.setGrabbed(true);" +
                            "    $0.a = 0;" +
                            "    $0.b = 0;" +
                            "}"
            );

            String body2 = (
                    "{" +
                            "    $0.a = org.lwjgl.input.Mouse.getDX();" +
                            "    $0.b = org.lwjgl.input.Mouse.getDY();" +
                            "}"
            );

            String body2invert = (
                    "{" +
                            "    $0.a = org.lwjgl.input.Mouse.getDX();" +
                            "    $0.b = -(org.lwjgl.input.Mouse.getDY());" +
                            "}"
            );

            // Mouse handling changed sometime during alpha
            boolean invert = "invert".equals(LegacyFixAgent.getSettings().get("invertMouse"));

            LFLogger.info("MOUSE Y INVERT: " + Boolean.toString(invert));

            if (mouseHelperMethods.length == 2) {
                mouseHelperMethods[1].setBody((invert ? body2invert : body2));
            } else {
                mouseHelperMethods[1].setBody(
                        "{" +
                                "    org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
                                "    org.lwjgl.input.Mouse.setGrabbed(false);" +
                                "}"
                );

                mouseHelperMethods[2].setBody((invert ? body2invert : body2));
            }

            inst.redefineClasses(new ClassDefinition[]{new ClassDefinition(Class.forName(mouseHelperClass.getName()), mouseHelperClass.toBytecode())});
        }

        // Replace all calls to Mouse.getDX() and Mouse.getDY() with 0
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader,
                                    String className,
                                    Class<?> classRedefined,
                                    ProtectionDomain domain,
                                    byte[] classfileBuffer) {

                CtClass clas = pool.getOrNull(className.replace("/", "."));
                if (clas == null || clas.getName().startsWith("org.lwjgl") || clas.getName().equals(mouseHelperClass.getName()))
                    return null;

                try {
                    clas.instrument(new ExprEditor() {
                        public void edit(MethodCall m) throws CannotCompileException {

                            if ("org.lwjgl.input.Mouse".equals(m.getClassName()) &&
                                    "getDX".equals(m.getMethodName()) &&
                                    "()I".equalsIgnoreCase(m.getSignature())) {

                                mouseDXYmatched = true;
                                m.replace("$_ = 0;");
                                LFLogger.info("Mouse.getDX() match!");

                            } else if ("org.lwjgl.input.Mouse".equals(m.getClassName()) &&
                                    "getDY".equals(m.getMethodName()) &&
                                    "()I".equalsIgnoreCase(m.getSignature())) {

                                mouseDXYmatched = true;
                                m.replace("$_ = 0;");
                                LFLogger.info("Mouse.getDY() match!");
                            }
                        }
                    });

                    if (mouseDXYmatched) {
                        mouseDXYmatched = false;
                        inst.removeTransformer(this); // job is done, don't fire any more classes
                        return clas.toBytecode();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                return null;
            }
        });

        // Some versions refer to setNativeCursor within methods of the Minecraft class,
        // we need to account for that too
        CtClass mouseClass = pool.get("org.lwjgl.input.Mouse");
        CtClass cursorClass = pool.get("org.lwjgl.input.Cursor");
        CtMethod setNativeCursorMethod = mouseClass.getDeclaredMethod("setNativeCursor", new CtClass[]{cursorClass});

        setNativeCursorMethod.setBody(
                "{" +
                        "    org.lwjgl.input.Mouse.setGrabbed($1 != null);" +
                        "    if ($1 == null) {" +
                        "        org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
                        "    }" +
                        "    return null;" + // we don't need this to return anything
                        "}"
        );

        inst.redefineClasses(new ClassDefinition[]{new ClassDefinition(Class.forName("org.lwjgl.input.Mouse"), mouseClass.toBytecode())});
    }
}
