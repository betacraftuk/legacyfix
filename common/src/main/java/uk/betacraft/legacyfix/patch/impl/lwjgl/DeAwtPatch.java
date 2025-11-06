package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.*;
import javassist.bytecode.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class DeAwtPatch extends Patch {
    private Exception thrown;

    public DeAwtPatch() {
        super("deawt", "Forces the game to use LWJGL's Display instead of AWT's Frame", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        CtClass minecraftAppletClass = GameClasses.findMinecraftAppletClass(patchPool);
        if (minecraftAppletClass == null) {
            throw new PatchException("No applet class could be found");
        }

        if (minecraftAppletClass.isFrozen()) {
            minecraftAppletClass.defrost();
        }

        CtField minecraftField = GameClasses.findMinecraftField(patchPool);
        CtClass minecraftClass = GameClasses.findMinecraftClass(patchPool);
        if (minecraftField == null || minecraftClass == null) {
            throw new PatchException("No main Minecraft field could be found");
        }

        CtField appletModeField = GameClasses.findAppletModeField(patchPool);
        CtMethod initMethod = minecraftAppletClass.getDeclaredMethod("init");

        initMethod.insertAfter("" +
            // Dispose of all AWT/Swing components
            "java.awt.Component parent = $0;" +
            "while (parent != null) {" +
            "    parent.setVisible(false);" +
            "    if (parent instanceof java.awt.Frame) {" +
            "        ((java.awt.Frame)parent).dispose();" +
            "    }" +
            "    parent = parent.getParent();" +
            "}" +
            // Set 'appletMode' to 'false' so the game handles LWJGL Display correctly
            "$0." + minecraftField.getName() + "." + appletModeField.getName() + " = false;" +
            // Start Minecraft
            "Thread mcThread = new Thread($0." + minecraftField.getName() + ", \"Minecraft main thread\");" +
            "mcThread.start();"
        );

        // Remove references to AWT
        initMethod.instrument(new ExprEditor() {
            public void edit(MethodCall mc) throws CannotCompileException {
                try {
                    if ("java.awt.Container".equals(mc.getMethod().getDeclaringClass().getName())) {
                        if ("setLayout".equals(mc.getMethodName())) {
                            Logger.debug("deAWT", "Found call to setLayout(), erasing");
                            mc.replace("{}");
                        } else if ("add".equals(mc.getMethodName())) {
                            Logger.debug("deAWT", "Found call to add(), erasing");
                            mc.replace("{}");
                        } else if ("validate".equals(mc.getMethodName())) {
                            Logger.debug("deAWT", "Found call to validate(), erasing");
                            mc.replace("{}");
                        }
                    } else if ("java.awt.Component".equals(mc.getMethod().getDeclaringClass().getName())) {
                        if ("setFocusable".equals(mc.getMethodName())) {
                            Logger.debug("deAWT", "Found call to setFocusable(), erasing");
                            mc.replace("{}");
                        } else if ("setFocusTraversalKeysEnabled".equals(mc.getMethodName())) {
                            Logger.debug("deAWT", "Found call to setFocusTraversalKeysEnabled(), erasing");
                            mc.replace("{}");
                        }
                    }
                } catch (NotFoundException e) {
                    DeAwtPatch.this.thrown = e;
                }
            }
        });

        if (this.thrown != null) {
            throw this.thrown;
        }

        patchPool.patchClass(minecraftAppletClass);

        CtClass javaAppletClass = patchPool.getClass("java.applet.Applet");
        CtMethod getParameterMethod = javaAppletClass.getDeclaredMethod("getParameter");

        getParameterMethod.setBody("" +
            "{" +
            "    Class launcherClass = java.lang.Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
            "    java.lang.reflect.Method method = launcherClass.getMethod(\"getValue\", new Class[] {String.class, String.class});" +
            "    return (String) method.invoke(null, new Object[] {$1, null});" +
            "}"
        );

        CtMethod getDocumentBaseMethod = javaAppletClass.getDeclaredMethod("getDocumentBase");
        getDocumentBaseMethod.insertBefore("return new java.net.URL(\"http://www.minecraft.net/\");");

        patchPool.patchClass(javaAppletClass);

        // deAWT main Minecraft class
        if (minecraftClass.isFrozen()) {
            minecraftClass.defrost();
        }

        // Replace calls to this.canvas.getWidth() & getHeight() with Display.getWidth() & getHeight()
        minecraftClass.instrument(new ExprEditor() {
            public void edit(MethodCall mc) throws CannotCompileException {
                try {
                    if ("java.awt.Canvas".equals(mc.getClassName()) && CT_INT.getName().equals(mc.getMethod().getReturnType().getName())) {
                        mc.replace("$_ = org.lwjgl.opengl.Display." + mc.getMethodName() + "();");
                    }
                } catch (NotFoundException e) {
                    DeAwtPatch.this.thrown = e;
                }
            }
        });

        if (this.thrown != null) {
            throw this.thrown;
        }

        // Nullify Canvas & MinecraftApplet
        CtConstructor minecraftConstructor = minecraftClass.getConstructors()[0];
        // Typical parameters of a Minecraft class constructor go like this:
        //  Component, Canvas, MinecraftApplet, int, int, boolean
        CtClass[] paramTypes = minecraftConstructor.getParameterTypes();

        int intCount = 0;

        for (int i = 0; i < paramTypes.length; i++) {
            String className = paramTypes[i].getName();

            if (className.equals("int") && intCount == 0) {
                // Resolution
                intCount++;

                minecraftConstructor.insertBefore("" +
                    "Class legacyfix = java.lang.Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
                    "$" + (i + 1) + " = ((Integer) legacyfix.getMethod(\"getWidth\", null).invoke(null, null)).intValue();" +
                    "$" + (i + 2) + " = ((Integer) legacyfix.getMethod(\"getHeight\", null).invoke(null, null)).intValue();"
                );
            } else if (className.equals("boolean")) {
                // Fullscreen
                minecraftConstructor.insertBefore("" +
                    "Class legacyfix = java.lang.Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
                    "$" + (i + 1) + " = ((Boolean) legacyfix.getMethod(\"getFullscreen\", null).invoke(null, null)).booleanValue();"
                );
            } else if (className.equals("java.awt.Canvas") || className.equals(minecraftAppletClass.getName())) {
                // Nullify Canvas & MinecraftApplet
                minecraftConstructor.insertBefore("$" + (i + 1) + " = null;");
            }
        }

        // Remove '!= null' checks for Canvas to support resizing
        for (CtMethod aMinecraftMethod : minecraftClass.getDeclaredMethods()) {
            ConstPool runConstPool = aMinecraftMethod.getMethodInfo().getConstPool();

            CodeAttribute codeAttribute = aMinecraftMethod.getMethodInfo().getCodeAttribute();
            if (codeAttribute == null) {
                continue;
            }

            CodeIterator codeIterator = codeAttribute.iterator();

            while (codeIterator.hasNext()) {
                int pos = codeIterator.next();

                eraseCanvasReferences(codeIterator, runConstPool, pos);
                eraseAppletReferences(codeIterator, runConstPool, pos, minecraftAppletClass);
                injectShutdownMethod(aMinecraftMethod, codeIterator, runConstPool, pos, minecraftClass);
            }
        }

        patchPool.patchClass(minecraftClass);
    }

    private void eraseCanvasReferences(CodeIterator codeIterator, ConstPool constPool, int pos) {
        if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
            codeIterator.byteAt(pos + 1) != Opcode.GETFIELD ||
            codeIterator.byteAt(pos + 4) != Opcode.IFNULL ||
            codeIterator.byteAt(pos + 7) != Opcode.ALOAD_0) {
            return;
        }

        final int futurePos = pos + 8;
        // A second ALOAD appears in triggerFullscreen(), IFNE appears in the runGameLoop() method
        if (codeIterator.byteAt(futurePos) != Opcode.ALOAD_0 &&
            codeIterator.byteAt(futurePos + 3) != Opcode.IFNE
        ) {
            return;
        }

        String refType = constPool.getFieldrefType(codeIterator.u16bitAt(pos + 2));
        if (!"Ljava/awt/Canvas;".equals(refType)) {
            return;
        }

        // Erase the check
        for (int i = 0; i < 7; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + i);
        }

        Logger.debug("deawt", "Erased Canvas references");
    }

    private void injectShutdownMethod(CtMethod shutdownMethod, CodeIterator codeIterator, ConstPool constPool, int pos, CtClass minecraftClass) throws NotFoundException, CannotCompileException, BadBytecode {
        if (codeIterator.byteAt(pos) != Opcode.INVOKESTATIC) {
            return;
        }

        String refName = constPool.getMethodrefName(codeIterator.u16bitAt(pos + 1));
        String refClassName = constPool.getMethodrefClassName(codeIterator.u16bitAt(pos + 1));
        if (!"destroy".equals(refName)) {
            return;
        }

        if (!"org.lwjgl.input.Keyboard".equals(refClassName)) {
            return;
        }

        CtMethod runMethod = minecraftClass.getDeclaredMethod("run");

        if (doesShutdownFinally(runMethod, shutdownMethod.getName())) {
            return;
        }

        addCatchToSetWorld(shutdownMethod, codeIterator, constPool);

        runMethod.insertAfter("$0." + shutdownMethod.getName() + "();", true);

        Logger.debug("deawt", "Injected shutdown method call into run() as finally");
    }

    private boolean doesShutdownFinally(CtMethod runMethod, String shutdownMethodName) throws BadBytecode {
        ConstPool constPool = runMethod.getMethodInfo().getConstPool();
        CodeAttribute codeAttribute = runMethod.getMethodInfo().getCodeAttribute();
        CodeIterator codeIterator = codeAttribute.iterator();

        while (codeIterator.hasNext()) {
            int pos = codeIterator.next();

            if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
                codeIterator.byteAt(pos + 1) != Opcode.INVOKEVIRTUAL) {
                continue;
            }

            String methodName = constPool.getMethodrefName(codeIterator.u16bitAt(pos + 2));
            String methodSign = constPool.getMethodrefType(codeIterator.u16bitAt(pos + 2));
            if (!methodName.equals(shutdownMethodName) || !"()V".equals(methodSign)) {
                continue;
            }

            return true;
        }

        return false;
    }

    private void addCatchToSetWorld(CtMethod shutdownMethod, CodeIterator codeIterator, ConstPool constPool) throws CannotCompileException {
        for (int pos = 0; pos < codeIterator.getCodeLength(); pos++) {
            if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
                codeIterator.byteAt(pos + 1) != Opcode.ACONST_NULL ||
                codeIterator.byteAt(pos + 2) != Opcode.INVOKEVIRTUAL ||

                (codeIterator.byteAt(pos + 5) == Opcode.GOTO &&
                    codeIterator.byteAt(pos + 8) == Opcode.ASTORE_1)
            ) {
                continue;
            }

            String methodName = constPool.getMethodrefName(codeIterator.u16bitAt(pos + 3));

            // Erase the method call
            for (int i = 0; i < 5; i++) {
                codeIterator.writeByte(Opcode.NOP, pos + i);
            }

            // @formatter:off
            shutdownMethod.insertAt(shutdownMethod.getMethodInfo().getLineNumber(pos),
                "try {" +
                    "    $0." + methodName + "(null);" +
                    "} catch (Throwable t) {}"
            );
            // @formatter:on

            Logger.debug("deawt", "Wrapped setWorld call in a try-catch block in shutdown method");
        }
    }

    private void eraseAppletReferences(CodeIterator codeIterator, ConstPool constPool, int pos, CtClass minecraftAppletClass) {
        eraseAppletReferencesClassic0_24(codeIterator, constPool, pos, minecraftAppletClass);
        eraseAppletReferencesClassic0_25(codeIterator, constPool, pos, minecraftAppletClass);
        eraseAppletReferencesClassic0_30(codeIterator, constPool, pos, minecraftAppletClass);
        eraseAppletReferencesIndev(codeIterator, constPool, pos);
    }

    private void eraseAppletReferencesClassic0_24(CodeIterator codeIterator, ConstPool constPool, int pos, CtClass minecraftAppletClass) {
        // This check always appears at the start of the method
        if (pos != 0) {
            return;
        }

        if (eraseAppletReferencesClassic0_24And0_25Shared("getDocumentBase", codeIterator, constPool, pos, minecraftAppletClass)) {
            Logger.debug("deawt", "Erased Classic 0.24/0.25 applet references");
        }
    }

    private void eraseAppletReferencesClassic0_25(CodeIterator codeIterator, ConstPool constPool, int pos, CtClass minecraftAppletClass) {
        if (pos != 23) {
            return;
        }

        if (eraseAppletReferencesClassic0_24And0_25Shared("getCodeBase", codeIterator, constPool, pos, minecraftAppletClass)) {
            Logger.debug("deawt", "Erased Classic 0.25 applet references");
        }
    }

    private boolean eraseAppletReferencesClassic0_24And0_25Shared(String appletMethodCall, CodeIterator codeIterator, ConstPool constPool, int pos, CtClass minecraftAppletClass) {
        if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
            codeIterator.byteAt(pos + 1) != Opcode.GETFIELD ||
            codeIterator.byteAt(pos + 4) != Opcode.INVOKEVIRTUAL ||
            codeIterator.byteAt(pos + 7) != Opcode.INVOKEVIRTUAL ||
            codeIterator.byteAt(pos + 10) != Opcode.INVOKEVIRTUAL ||
            codeIterator.byteAt(pos + 13) != Opcode.LDC ||
            codeIterator.byteAt(pos + 15) != Opcode.INVOKEVIRTUAL ||
            codeIterator.byteAt(pos + 18) != Opcode.IFNE ||
            codeIterator.byteAt(pos + 21) != Opcode.ACONST_NULL ||
            codeIterator.byteAt(pos + 22) != Opcode.ASTORE_1) {
            return false;
        }

        String refType = constPool.getFieldrefType(codeIterator.u16bitAt(pos + 2));
        if (!("L" + minecraftAppletClass.getName().replace('.', '/') + ";").equals(refType)) {
            return false;
        }

        String refName = constPool.getMethodrefName(codeIterator.u16bitAt(pos + 5));
        if (!appletMethodCall.equals(refName)) {
            return false;
        }

        int ldcPos = codeIterator.byteAt(pos + 14);
        if (!isString(constPool, ldcPos)) {
            return false;
        }

        String host = constPool.getStringInfo(ldcPos);
        if (!"minecraft.net".equals(host)) {
            return false;
        }

        // Erase the check
        for (int i = 0; i < 23; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + i);
        }

        return true;
    }

    private void eraseAppletReferencesClassic0_30(CodeIterator codeIterator, ConstPool constPool, int pos, CtClass minecraftAppletClass) {
        // This check always appears at the start of the method
        if (pos != 0) {
            return;
        }

        if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
            codeIterator.byteAt(pos + 1) != Opcode.GETFIELD ||
            codeIterator.byteAt(pos + 4) != Opcode.IFNULL ||
            codeIterator.byteAt(pos + 7) != Opcode.ALOAD_0 ||
            codeIterator.byteAt(pos + 8) != Opcode.GETFIELD ||
            codeIterator.byteAt(pos + 11) != Opcode.INVOKEVIRTUAL) {
            return;
        }

        String refType = constPool.getFieldrefType(codeIterator.u16bitAt(pos + 2));
        if (!("L" + minecraftAppletClass.getName().replace('.', '/') + ";").equals(refType)) {
            return;
        }

        String refName = constPool.getMethodrefName(codeIterator.u16bitAt(pos + 12));
        if (!"getDocumentBase".equals(refName)) {
            return;
        }

        // Erase the check
        for (int i = 0; i < 81; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + i);
        }

        Logger.debug("deawt", "Erased Classic 0.30 applet references");
    }

    private void eraseAppletReferencesIndev(CodeIterator codeIterator, ConstPool constPool, int pos) {
        if (codeIterator.getCodeLength() <= pos + 30) {
            return;
        }

        if (codeIterator.byteAt(pos) != Opcode.NEW ||
            (codeIterator.byteAt(pos + 29) != Opcode.LDC &&
                codeIterator.byteAt(pos + 29) != Opcode.LDC_W)) {
            return;
        }

        // Support both LDC and LDC_W for modern mods support
        String value;
        if (codeIterator.byteAt(pos + 29) == Opcode.LDC && isString(constPool, codeIterator.byteAt(pos + 30))) {
            value = constPool.getStringInfo(codeIterator.byteAt(pos + 30));
        } else if (codeIterator.byteAt(pos + 29) == Opcode.LDC_W && isUtf8(constPool, codeIterator.byteAt(pos + 30))) {
            value = constPool.getStringInfo(codeIterator.u16bitAt(pos + 30));
        } else {
            value = null;
        }

        if (!"?n=".equals(value)) {
            return;
        }

        // Determine how far to erase
        int eraseTo = -1;
        for (int i = 0; i < codeIterator.getCodeLength() - pos; i++) {
            if (codeIterator.byteAt(pos + i) == Opcode.IFEQ) {
                eraseTo = i + 3;
                break;
            }
        }

        // Erase the check
        for (int i = 0; i < eraseTo; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + i);
        }

        if (eraseTo != -1) {
            Logger.debug("deawt", "Erased Indev applet references");
        }
    }
}
