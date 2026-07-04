package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

import javax.imageio.ImageIO;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;

public class DeAwtPatch extends Patch {
    public static final String SHUTDOWN_HOOK = "" +
        "System.out.println(\"Shutting down...\");" +
        "try {" +
        "    org.lwjgl.input.Mouse.destroy();" +
        "    org.lwjgl.input.Keyboard.destroy();" +
        "    org.lwjgl.openal.AL.destroy();" +
        "} catch (Throwable ignored) {" +
        "} finally {" +
        "    org.lwjgl.opengl.Display.destroy();" +
        "    System.exit(0);" +
        "}";

    public DeAwtPatch() {
        super("deawt", "Forces the game to use LWJGL's windowing system", true);
    }

    public void apply(final PatchPool patchPool) throws Exception {
        final String minecraftAppletClass = GameClasses.findMinecraftAppletClass(patchPool);
        if (minecraftAppletClass == null) {
            throw new PatchException("No applet class found");
        }

        final String minecraftClass = GameClasses.findMinecraftClass(patchPool);
        final String minecraftFieldName = GameClasses.findMinecraftFieldName(patchPool);
        if (minecraftFieldName == null || minecraftClass == null) {
            throw new PatchException("No game instance found");
        }

        final String appletModeFieldName = GameClasses.findAppletModeFieldName(patchPool);
        patchPool.addCtTransformer(minecraftAppletClass, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod cleanupMethod = findCleanupMethod(ctClass);
                if (cleanupMethod != null) {
                    Logger.debug("Found applet cleanup method: " + cleanupMethod.getName());
                    cleanupMethod.insertAfter(SHUTDOWN_HOOK);
                }

                if (appletModeFieldName != null) {
                    CtMethod initMethod = ctClass.getDeclaredMethod("init");
                    initMethod.insertAfter("$0." + minecraftFieldName + "." + appletModeFieldName + " = false;");
                }
            }
        });

        patchPool.addCtTransformer("java.applet.Applet", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                if (ctClass.isFrozen()) {
                    ctClass.defrost();
                }
                ctClass.getDeclaredMethod("getDocumentBase").setBody("{ return new java.net.URL(\"http://www.minecraft.net/\"); }");
            }
        });

        patchPool.addCtTransformer(minecraftClass, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                transformGameClass(ctClass);
            }
        });

        patchPool.addCtTransformer("org.lwjgl.opengl.Display", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                transformDisplay(ctClass);
            }
        });
    }

    private void transformGameClass(CtClass gameClass) throws Exception {
        CtMethod updateMethod = findUpdateMethod(gameClass, gameClass.getDeclaredMethod("run"));
        if (updateMethod == null) {
            throw new PatchException("No update method found");
        }

        if (isFinallyBlockEmpty(updateMethod)) {
            updateMethod.insertAfter(SHUTDOWN_HOOK, true);
        }

        final CtMethod resizeMethod = findResizeMethod(gameClass);
        if (resizeMethod == null) {
            Logger.debug("No resize method found");
            return;
        }

        final CtField[] sizeFields = findSizeFields(gameClass);
        if (sizeFields == null || sizeFields[0] == null || sizeFields[1] == null) {
            Logger.debug("No width/height fields found");
            return;
        }

        final CtField canvasField = findCanvasField(gameClass);
        updateMethod.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (!"isCloseRequested".equals(m.getMethodName())) return;

                String canvasUpdate = "";
                if (canvasField != null) {
                    canvasUpdate = "if (this." + canvasField.getName() + " != null) { this." + canvasField.getName() + ".setSize(dW, dH); }";
                }

                m.replace("" +
                    "$_ = $proceed($$);" +
                    "if (org.lwjgl.opengl.Display.isCreated()) {" +
                    "   int dW = org.lwjgl.opengl.Display.getWidth();" +
                    "   int dH = org.lwjgl.opengl.Display.getHeight();" +
                    "   if (dW != this." + sizeFields[0].getName() + " || dH != this." + sizeFields[1].getName() + ") {" +
                    "       this." + resizeMethod.getName() + "(dW, dH);" +
                    "       this." + sizeFields[0].getName() + " = dW;" +
                    "       this." + sizeFields[1].getName() + " = dH;" +
                    "       " + canvasUpdate +
                    "   }" +
                    "}"
                );
            }
        });

        MethodInfo mi = updateMethod.getMethodInfo();
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) {
            return;
        }

        ConstPool cp = mi.getConstPool();
        CodeIterator it = ca.iterator();

        while (it.hasNext()) {
            int pos = it.next();
            if (it.byteAt(pos) != Opcode.GETFIELD) continue;

            int fieldIndex = it.u16bitAt(pos + 1);
            String fieldType = cp.getFieldrefType(fieldIndex);
            if (!"Ljava/awt/Canvas;".equals(fieldType)) continue;

            int next = it.next();
            if (it.byteAt(next) == Opcode.IFNONNULL) {
                it.writeByte(Opcode.POP, next);
                it.writeByte(Opcode.NOP, next + 1);
                it.writeByte(Opcode.NOP, next + 2);
                ca.computeMaxStack();
                break;
            }
        }
    }

    private void transformDisplay(CtClass displayClass) throws Exception {
        try {
            Icons.loadIcons((String) Agent.getSettings().get("lf.icon"));
        } catch (Exception e) {
            Logger.error(this, e);
        }

        if (displayClass.isFrozen()) {
            displayClass.defrost();
        }

        displayClass.getDeclaredMethod("setParent").setBody("" +
            "{" +
            "    if ($1 == null) return;" +
            "    java.awt.Component child = (java.awt.Component)$1;" +
            "    java.awt.Container parent = child.getParent();" +
            "    while (parent != null && !(parent instanceof java.awt.Frame)) parent = parent.getParent();" +
            "    if (parent == null) return;" +
            "    java.awt.Frame frame = (java.awt.Frame) parent;" +
            "    frame.setVisible(false);" +
            "    Class clazz = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.lwjgl.DeAwtPatch$FrameHider\");" +
            "    frame.addHierarchyListener((java.awt.event.HierarchyListener) clazz.newInstance());" +
            "    org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(child.getWidth(), child.getHeight()));" +
            "    org.lwjgl.opengl.Display.setResizable(true);" +
            "}"
        );

        // older Forge checks the game version by title in format "Minecraft Minecraft <VERSION>"
        displayClass.getDeclaredMethod("getTitle").insertBefore("" +
            "String val = System.getProperty(\"lf.deawt.originalTitle\");" +
            "if (val != null) {" +
            "    return val;" +
            "}"
        );

        displayClass.getDeclaredMethod("setTitle").insertBefore("" +
            "if ($1 != null && System.getProperty(\"lf.deawt.originalTitle\") == null) {" +
            "    System.setProperty(\"lf.deawt.originalTitle\", $1);" +
            "}" +
            "$1 = $1.replace(\"Minecraft Minecraft\", \"Minecraft\");" +
            "java.lang.reflect.Field f16 = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.lwjgl.DeAwtPatch$Icons\").getDeclaredField(\"pixels16\");" +
            "f16.setAccessible(true);" +
            "java.nio.ByteBuffer pix16 = f16.get(null);" +
            "java.lang.reflect.Field f32 = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.lwjgl.DeAwtPatch$Icons\").getDeclaredField(\"pixels32\");" +
            "f32.setAccessible(true);" +
            "java.nio.ByteBuffer pix32 = f32.get(null);" +
            "if (pix16 != null && pix32 != null) {" +
            "    org.lwjgl.opengl.Display.setIcon(new java.nio.ByteBuffer[] {pix16, pix32});" +
            "}"
        );
    }

    private CtField findCanvasField(CtClass gameClass) throws Exception {
        for (CtField field : gameClass.getDeclaredFields()) {
            if ("java.awt.Canvas".equals(field.getType().getName())) {
                return field;
            }
        }
        return null;
    }

    private CtField[] findSizeFields(CtClass gameClass) throws Exception {
        CtMethod resizeMethod = findResizeMethod(gameClass);
        if (resizeMethod == null) {
            return null;
        }

        CodeAttribute ca = resizeMethod.getMethodInfo().getCodeAttribute();
        if (ca == null) {
            return null;
        }

        ConstPool cp = ca.getConstPool();
        CodeIterator it = ca.iterator();

        CtField[] fields = new CtField[2];
        int fieldIndex = 0;
        while (it.hasNext()) {
            if (fieldIndex == 2) break;

            int pos = it.next();
            if (it.byteAt(pos) == Opcode.PUTFIELD) {
                int index = it.u16bitAt(pos + 1);
                String fieldName = cp.getFieldrefName(index);
                CtField f = gameClass.getField(fieldName);

                fields[fieldIndex] = f;
                fieldIndex++;
            }
        }

        return fields;
    }

    private CtMethod findCleanupMethod(CtClass appletClass) {
        for (CtMethod method : appletClass.getDeclaredMethods()) {
            if (!"()V".equals(method.getSignature())) continue;

            MethodInfo mi = method.getMethodInfo();
            CodeAttribute ca = mi.getCodeAttribute();
            if (ca == null) continue;

            ConstPool cp = mi.getConstPool();
            CodeIterator it = ca.iterator();
            boolean callsRemoveAll = false;
            boolean callsValidate = false;

            try {
                while (it.hasNext()) {
                    int pos = it.next();
                    int opcode = it.byteAt(pos);
                    if (opcode != Opcode.INVOKEVIRTUAL) continue;

                    int methodIndex = it.u16bitAt(pos + 1);
                    String name = cp.getMethodrefName(methodIndex);
                    String signature = cp.getMethodrefType(methodIndex);
                    if (!"()V".equals(signature)) continue;

                    if ("removeAll".equals(name)) {
                        callsRemoveAll = true;
                    } else if ("validate".equals(name)) {
                        callsValidate = true;
                    }

                    if (callsRemoveAll && callsValidate) {
                        return method;
                    }
                }
            } catch (BadBytecode ignored) {
            }
        }

        return null;
    }

    private CtMethod findResizeMethod(CtClass gameClass) {
        for (CtMethod method : gameClass.getDeclaredMethods()) {
            if ("(II)V".equals(method.getSignature())) return method;
        }

        return null;
    }

    private CtMethod findUpdateMethod(CtClass gameClass, CtMethod runMethod) throws Exception {
        MethodInfo mi = runMethod.getMethodInfo();
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) {
            return null;
        }

        ConstPool cp = mi.getConstPool();
        ExceptionTable et = ca.getExceptionTable();
        if (et == null) {
            return null;
        }

        int exIndex = -1;
        for (int i = 0; i < et.size(); i++) {
            int catchType = et.catchType(i);
            if (catchType == 0) continue;

            if ("java.lang.OutOfMemoryError".equals(cp.getClassInfo(catchType))) {
                exIndex = i;
                break;
            }
        }

        if (exIndex != -1) {
            int startPc = et.startPc(exIndex);
            int endPc = et.endPc(exIndex);
            CodeIterator it = ca.iterator();

            while (it.hasNext()) {
                int pos = it.next();
                if (pos < startPc || pos >= endPc) continue;

                if (it.byteAt(pos) != Opcode.INVOKESPECIAL) continue;

                int methodIndex = it.u16bitAt(pos + 1);
                if ("()V".equals(cp.getMethodrefType(methodIndex))
                    && gameClass.getName().equals(cp.getMethodrefClassName(methodIndex))
                ) {
                    return gameClass.getDeclaredMethod(cp.getMethodrefName(methodIndex));
                }
            }
        }

        CodeIterator it = ca.iterator();
        while (it.hasNext()) {
            int pos = it.next();
            if (it.byteAt(pos) != Opcode.INVOKESTATIC) continue;

            int methodIndex = it.u16bitAt(pos + 1);
            if ("org.lwjgl.opengl.Display".equals(cp.getMethodrefClassName(methodIndex))) {
                String methodName = cp.getMethodrefName(methodIndex);
                if ("update".equals(methodName) || "isCloseRequested".equals(methodName)) {
                    return runMethod;
                }
            }
        }

        return null;
    }

    private boolean isFinallyBlockEmpty(CtMethod method) {
        MethodInfo mi = method.getMethodInfo();
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) return false;

        ExceptionTable et = ca.getExceptionTable();
        if (et == null || et.size() == 0) return false;

        boolean foundFinally = false;
        CodeIterator it = ca.iterator();
        for (int i = 0; i < et.size(); i++) {
            if (et.catchType(i) != 0) continue;

            foundFinally = true;
            int pos = et.handlerPc(i);
            it.move(pos);

            try {
                while (it.hasNext()) {
                    int index = it.next();
                    int opcode = it.byteAt(index);

                    if (opcode == Opcode.ATHROW) {
                        break;
                    }

                    if (opcode == Opcode.INVOKEVIRTUAL || opcode == Opcode.INVOKESTATIC ||
                        opcode == Opcode.INVOKESPECIAL || opcode == Opcode.INVOKEINTERFACE) {
                        return false;
                    }
                }
            } catch (BadBytecode e) {
                break;
            }
        }

        return foundFinally;
    }

    @SuppressWarnings("unused")
    public static class FrameHider implements HierarchyListener {
        public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
            if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }

            Frame frame = (Frame) hierarchyEvent.getSource();
            if (frame.isShowing() || frame.isVisible()) {
                try { // Race condition workaround for Linux
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }

                frame.setVisible(false);
            }
        }
    }

    public static class Icons {
        static ByteBuffer pixels16 = null;
        static ByteBuffer pixels32 = null;

        public static void loadIcons(String iconPath) throws IOException {
            if (iconPath != null) {
                File iconFile = new File(iconPath);

                if (iconFile.exists() && iconFile.isFile()) {
                    pixels32 = getIconForLWJGL(new FileInputStream(iconFile), 32);
                    pixels16 = getIconForLWJGL(new FileInputStream(iconFile), 16);
                } else {
                    Logger.error("No icon found at " + iconPath);
                    pixels16 = getIconForLWJGL(DeAwtPatch.class.getResourceAsStream("/favicon.png"), 16);
                    pixels32 = getIconForLWJGL(DeAwtPatch.class.getResourceAsStream("/favicon.png"), 32);
                }
            } else {
                pixels16 = getIconForLWJGL(DeAwtPatch.class.getResourceAsStream("/favicon.png"), 16);
                pixels32 = getIconForLWJGL(DeAwtPatch.class.getResourceAsStream("/favicon.png"), 32);
            }
        }

        private static ByteBuffer getIconForLWJGL(InputStream stream, int resolution) throws IOException {
            final Image read = ImageIO.read(stream).getScaledInstance(resolution, resolution, Image.SCALE_SMOOTH);
            BufferedImage bufImg = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB);
            Graphics g = bufImg.getGraphics();
            g.drawImage(read, 0, 0, null);
            g.dispose();

            final int[] rgb = bufImg.getRGB(0, 0, resolution, resolution, null, 0, resolution);
            final ByteBuffer allocate = ByteBuffer.allocate(4 * rgb.length);

            for (final int n : rgb) {
                allocate.putInt(n << 8 | (n >> 24 & 0xFF));
            }

            allocate.flip();
            return allocate;
        }
    }
}
