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
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class DeAwtPatch extends Patch {
    public DeAwtPatch() {
        super("deawt", "Forces the game to use LWJGL's windowing system", true);
    }

    public void apply(PatchPool patchPool) throws Exception {
        CtClass minecraftAppletClass = GameClasses.findMinecraftAppletClass(patchPool);
        if (minecraftAppletClass == null) {
            throw new PatchException("No applet class found");
        }

        if (minecraftAppletClass.isFrozen()) {
            minecraftAppletClass.defrost();
        }

        final CtField minecraftField = GameClasses.findMinecraftField(patchPool);
        final CtClass minecraftClass = GameClasses.findMinecraftClass(patchPool);
        if (minecraftField == null || minecraftClass == null) {
            throw new PatchException("No game instance found");
        }

        if (minecraftClass.isFrozen()) {
            minecraftClass.defrost();
        }

        patchApplet(patchPool, minecraftAppletClass, minecraftField);
        patchGameClass(patchPool, minecraftClass);
        patchDisplay(patchPool);
    }

    private void patchApplet(PatchPool patchPool, CtClass appletClass, CtField gameInstanceField) throws Exception {
        CtField appletModeField = GameClasses.findAppletModeField(patchPool);
        if (appletModeField != null) {
            CtMethod initMethod = appletClass.getDeclaredMethod("init");
            initMethod.insertAfter("$0." + gameInstanceField.getName() + "." + appletModeField.getName() + " = false;");
            patchPool.patchClass(appletClass);
        }

        CtClass javaAppletClass = patchPool.getClass("java.applet.Applet");
        if (javaAppletClass.isFrozen()) {
            javaAppletClass.defrost();
        }

        javaAppletClass.getDeclaredMethod("getDocumentBase").setBody("{ return new java.net.URL(\"http://www.minecraft.net/\"); }");
        patchPool.patchClass(javaAppletClass);
    }

    private void patchGameClass(PatchPool patchPool, CtClass gameClass) throws Exception {
        CtMethod updateMethod = findUpdateMethod(gameClass, gameClass.getDeclaredMethod("run"));
        if (updateMethod == null) {
            throw new PatchException("No update method found");
        }

        final CtMethod resizeMethod = findResizeMethod(gameClass);
        if (resizeMethod == null) {
            throw new PatchException("No resize method found");
        }

        final CtField[] sizeFields = findSizeFields(gameClass);
        if (sizeFields == null || sizeFields[0] == null || sizeFields[1] == null) {
            throw new PatchException("No width/height fields found");
        }

        if (isFinallyBlockEmpty(updateMethod)) {
            updateMethod.insertAfter("" +
                "System.out.println(\"Shutting down...\");" +
                "try {" +
                "    org.lwjgl.input.Mouse.destroy();" +
                "    org.lwjgl.input.Keyboard.destroy();" +
                "    org.lwjgl.openal.AL.destroy();" +
                "} catch (Throwable ignored) {" +
                "} finally {" +
                "    org.lwjgl.opengl.Display.destroy();" +
                "    System.exit(0);" +
                "}",
                true
            );
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

        patchPool.patchClass(gameClass);
    }

    private void patchDisplay(PatchPool patchPool) throws Exception {
        try {
            Icons.loadIcons((String) Agent.getSettings().get("lf.icon"));
        } catch (Exception e) {
            Logger.error(this, e);
        }

        CtClass displayClass = patchPool.getClass("org.lwjgl.opengl.Display");
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

        displayClass.getDeclaredMethod("setTitle").insertBefore("" +
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

        patchPool.patchClass(displayClass);
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

        CodeIterator it = ca.iterator();
        for (int i = 0; i < et.size(); i++) {
            if (et.catchType(i) != 0) continue;

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

        return true;
    }

    @SuppressWarnings("unused")
    public static class FrameHider implements HierarchyListener {
        public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
            if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }

            Frame frame = (Frame) hierarchyEvent.getSource();
            if (frame.isShowing() || frame.isVisible()) {
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
