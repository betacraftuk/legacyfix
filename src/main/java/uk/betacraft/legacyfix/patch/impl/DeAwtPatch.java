package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;
import uk.betacraft.legacyfix.util.IconUtils;

public class DeAwtPatch extends Patch {
    private Exception thrown;

    public DeAwtPatch() {
        super("deawt", "Forces the game to use LWJGL's Display instead of AWT's Frame", true);
    }

    @Override
    public void apply(final Instrumentation inst) throws Exception {
        try {
            IconUtils.loadIcons((String) LegacyFixAgent.getSettings().get("icon"));
        } catch (Exception e) {
            LFLogger.error(this, e);
        }

        CtClass minecraftAppletClass = PatchHelper.findMinecraftAppletClass(pool);
        if (minecraftAppletClass == null) {
            throw new PatchException("No applet class could be found");
        }

        if (minecraftAppletClass.isFrozen())
            minecraftAppletClass.defrost();

        CtField minecraftField = PatchHelper.findMinecraftField(pool);
        CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
        if (minecraftField == null || minecraftClass == null) {
            throw new PatchException("No main Minecraft field could be found");
        }

        CtField appletModeField = PatchHelper.findAppletModeField(pool);
        CtMethod initMethod = minecraftAppletClass.getDeclaredMethod("init");

        // @formatter:off
        initMethod.insertAfter(
            // Set 'appletMode' to 'false' so the game handles LWJGL Display correctly
            "$0." + minecraftField.getName() + "." + appletModeField.getName() + " = false;" +
            // Start Minecraft
            "Thread mcThread = new Thread($0." + minecraftField.getName() + ", \"Minecraft main thread\");" +
            "mcThread.start();"
        );
        // @formatter:on

        // Remove references to AWT
        initMethod.instrument(new ExprEditor() {
            public void edit(MethodCall mc) throws CannotCompileException {
                try {
                    if ("java.awt.Container".equals(mc.getMethod().getDeclaringClass().getName())) {
                        if ("setLayout".equals(mc.getMethodName())) {
                            if (LegacyFixAgent.isDebug())
                                LFLogger.info("deAWT", "Found call to setLayout(), erasing");

                            mc.replace("{}");
                        } else if ("add".equals(mc.getMethodName())) {
                            if (LegacyFixAgent.isDebug())
                                LFLogger.info("deAWT", "Found call to add(), erasing");

                            mc.replace("{}");
                        } else if ("validate".equals(mc.getMethodName())) {
                            if (LegacyFixAgent.isDebug())
                                LFLogger.info("deAWT", "Found call to validate(), erasing");

                            mc.replace("{}");
                        }
                    } else if ("java.awt.Component".equals(mc.getMethod().getDeclaringClass().getName())) {
                        if ("setFocusable".equals(mc.getMethodName())) {
                            if (LegacyFixAgent.isDebug())
                                LFLogger.info("deAWT", "Found call to setFocusable(), erasing");

                            mc.replace("{}");
                        } else if ("setFocusTraversalKeysEnabled".equals(mc.getMethodName())) {
                            if (LegacyFixAgent.isDebug())
                                LFLogger.info("deAWT", "Found call to setFocusTraversalKeysEnabled(), erasing");

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

        inst.redefineClasses(new ClassDefinition(Class.forName(minecraftAppletClass.getName()), minecraftAppletClass.toBytecode()));

        CtClass javaAppletClass = pool.get("java.applet.Applet");
        CtMethod getParameterMethod = javaAppletClass.getDeclaredMethod("getParameter");

        // @formatter:off
        getParameterMethod.setBody(
            "{" +
            "    Class launcherClass = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
            "    java.lang.reflect.Method method = launcherClass.getMethod(\"getValue\", new Class[] {String.class, String.class});" +
            "    return method.invoke(null, new Object[] {$1, null});" +
            "}"
        );
        // @formatter:on

        CtMethod getDocumentBaseMethod = javaAppletClass.getDeclaredMethod("getDocumentBase");

        // @formatter:off
        getDocumentBaseMethod.insertBefore(
            "return new java.net.URL(\"http://www.minecraft.net/\");"
        );
        // @formatter:on

        inst.redefineClasses(new ClassDefinition(Class.forName(javaAppletClass.getName()), javaAppletClass.toBytecode()));

        // TODO: Investigate if this is at all useful in resizing versions before in-0111
//        CtClass guiScreenClass = null;
//        CtMethod guiScreenInitMethod = null;
//        CtField guiScreenField = null;
//
//        CtField[] minecraftClassFields = minecraftClass.getDeclaredFields();
//
//        for (CtField field : minecraftClassFields) {
//            CtMethod[] methods = field.getType().getDeclaredMethods();
//            for (CtMethod method : methods) {
//                CtClass[] params = method.getParameterTypes();
//                if (params.length == 3 && params[0].getName().equals(minecraftClass.getName()) && params[1].getName().equals("int") && params[2].getName().equals("int")) {
//                    guiScreenClass = field.getType();
//                    guiScreenInitMethod = method;
//                    guiScreenField = field;
//
//                    LFLogger.info("Found match for GuiScreen: " + guiScreenClass.getName());
//                    break;
//                }
//            }
//
//            if (guiScreenField != null) {
//                break;
//            }
//        }
//
//        CtClass inGameHudClass = null;
//        CtField inGameHudField = null;
//
//        for (CtField field : minecraftClassFields) {
//            CtConstructor[] constrs = field.getType().getDeclaredConstructors();
//            for (CtConstructor constr : constrs) {
//                CtClass[] params = constr.getParameterTypes();
//                if (params.length == 3 && params[0].getName().equals(minecraftClass.getName()) && params[1].getName().equals("int") && params[2].getName().equals("int")) {
//                    inGameHudClass = field.getType();
//                    inGameHudField = field;
//
//                    LFLogger.info("Found match for InGameHud: " + field.getName() + " / " + inGameHudClass.getName());
//                    break;
//                }
//            }
//
//            if (inGameHudField != null) {
//                break;
//            }
//        }
//
//        // Find resolution fields in InGameHud class
//        CtField[] inGameHudResFields = new CtField[]{null, null};
//
//        if (inGameHudClass != null) {
//            // We take for granted that first two int fields are: width & height
//            int intOccurences = 0;
//            for (CtField field : inGameHudClass.getDeclaredFields()) {
//                String className = field.getType().getName();
//
//                if (className.equals("int") && intOccurences < 2) {
//                    LFLogger.info("Found InGameHud resolution field (" + intOccurences + "): " + field.getName());
//
//                    inGameHudResFields[intOccurences] = field;
//                    intOccurences++;
//
//                    if (intOccurences > 1) {
//                        break;
//                    }
//                }
//            }
//        }
//
//        // Find the resolution fields in Minecraft class
//        CtField[] minecraftResFields = new CtField[]{null, null};
//
//        // We take for granted that first two int fields are: width & height
//        int intOccurences = 0;
//        for (CtField field : minecraftClass.getDeclaredFields()) {
//            String className = field.getType().getName();
//            if (className.equals("int") && intOccurences < 2) {
//                LFLogger.info("Found Minecraft resolution field (" + intOccurences + "): " + field.getName());
//
//                minecraftResFields[intOccurences] = field;
//                intOccurences++;
//
//                if (intOccurences > 1) {
//                    break;
//                }
//            }
//        }

        // deAWT main Minecraft class
        if (minecraftClass.isFrozen()) {
            minecraftClass.defrost();
        }

        // Replace calls to this.canvas.getWidth() & getHeight() with Display.getWidth() & getHeight()
        minecraftClass.instrument(new ExprEditor() {
            public void edit(MethodCall mc) throws CannotCompileException {
                try {
                    if ("java.awt.Canvas".equals(mc.getClassName()) && PatchHelper.intClass.getName().equals(mc.getMethod().getReturnType().getName())) {
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
        String width = LegacyFixAgent.getSetting("lf.width", "854");
        String height = LegacyFixAgent.getSetting("lf.height", "480");
        String fullscreen = LegacyFixAgent.getSetting("lf.fullscreen", "false");

        for (int i = 0; i < paramTypes.length; i++) {
            String className = paramTypes[i].getName();

            // Resolution
            if (className.equals("int")) {
                intCount++;

                minecraftConstructor.insertBefore("$" + (i + 1) + " = " + (intCount == 1 ? width : height) + ";");
            }

            // Fullscreen
            if (className.equals("boolean")) {
                minecraftConstructor.insertBefore("$" + (i + 1) + " = " + fullscreen + ";");
            }

            // Nullify Canvas & MinecraftApplet
            if (className.equals("java.awt.Canvas") || className.equals(minecraftAppletClass.getName())) {
                minecraftConstructor.insertBefore("$" + (i + 1) + " = null;");
            }
        }

        // Remove '!= null' checks for Canvas to support resizing
        for (CtMethod aMinecraftMethod : minecraftClass.getDeclaredMethods()) {
            ConstPool runConstPool = aMinecraftMethod.getMethodInfo().getConstPool();

            CodeAttribute codeAttribute = aMinecraftMethod.getMethodInfo().getCodeAttribute();
            if (codeAttribute == null)
                continue;

            CodeIterator codeIterator = codeAttribute.iterator();

            while (codeIterator.hasNext()) {
                int pos = codeIterator.next();

                eraseCanvasReferences(codeIterator, runConstPool, pos);
                eraseAppletReferences(codeIterator, runConstPool, pos, minecraftAppletClass);
            }
        }

        inst.redefineClasses(new ClassDefinition(Class.forName(minecraftClass.getName()), minecraftClass.toBytecode()));
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
                codeIterator.byteAt(futurePos + 3) != Opcode.IFNE) {
            return;
        }

        String refType = constPool.getFieldrefType(codeIterator.u16bitAt(pos + 2));
        if (!"Ljava/awt/Canvas;".equals(refType))
            return;

        // Erase the check
        for (int i = 0; i < 7; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + i);
        }

        if (LegacyFixAgent.isDebug()) {
            LFLogger.info("deawt", "Erased Canvas references");
        }
    }

    private void eraseAppletReferences(CodeIterator codeIterator, ConstPool constPool, int pos, CtClass minecraftAppletClass) {
        eraseAppletReferencesClassic(codeIterator, constPool, pos, minecraftAppletClass);
        eraseAppletReferencesIndev(codeIterator, constPool, pos);
    }

    private void eraseAppletReferencesClassic(CodeIterator codeIterator, ConstPool constPool, int pos, CtClass minecraftAppletClass) {
        // This check always appears at the start of the method
        if (pos != 0)
            return;

        if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
                codeIterator.byteAt(pos + 1) != Opcode.GETFIELD ||
                codeIterator.byteAt(pos + 4) != Opcode.IFNULL ||
                codeIterator.byteAt(pos + 7) != Opcode.ALOAD_0 ||
                codeIterator.byteAt(pos + 8) != Opcode.GETFIELD ||
                codeIterator.byteAt(pos + 11) != Opcode.INVOKEVIRTUAL) {
            return;
        }

        String refType = constPool.getFieldrefType(codeIterator.u16bitAt(pos + 2));
        if (!("L" + minecraftAppletClass.getName().replace('.', '/') + ";").equals(refType))
            return;

        String refName = constPool.getMethodrefName(codeIterator.u16bitAt(pos + 12));
        if (!"getDocumentBase".equals(refName))
            return;

        // Erase the check
        for (int i = 0; i < 81; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + i);
        }

        if (LegacyFixAgent.isDebug()) {
            LFLogger.info("deawt", "Erased Classic applet references");
        }
    }

    private void eraseAppletReferencesIndev(CodeIterator codeIterator, ConstPool constPool, int pos) {
        if (codeIterator.getCodeLength() <= pos + 29)
            return;

        if (codeIterator.byteAt(pos) != Opcode.NEW ||
                (codeIterator.byteAt(pos + 29) != Opcode.LDC &&
                codeIterator.byteAt(pos + 29) != Opcode.LDC_W)) {
            return;
        }

        // Support both LDC and LDC_W for modern mods support
        String value;
        if (codeIterator.byteAt(pos + 29) == Opcode.LDC && PatchHelper.isString(constPool, codeIterator.byteAt(pos + 30))) {
            value = constPool.getStringInfo(codeIterator.byteAt(pos + 30));
        } else if (codeIterator.byteAt(pos + 29) == Opcode.LDC_W && PatchHelper.isUtf8(constPool, codeIterator.u16bitAt(pos + 30))) {
            value = constPool.getStringInfo(codeIterator.u16bitAt(pos + 30));
        } else {
            value = null;
        }

        if (!"?n=".equals(value))
            return;

        // Determine how far to erase
        int eraseTo = -1;
        for (int i = 0; i < codeIterator.getCodeLength() - pos; i++) {
            if (codeIterator.byteAt(pos + i) == Opcode.IFEQ) {
                eraseTo = i + 3;
            }
        }

        // Erase the check
        for (int i = 0; i < eraseTo; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + i);
        }

        if (eraseTo != -1 && LegacyFixAgent.isDebug()) {
            LFLogger.info("deawt", "Erased Indev applet references");
        }
    }
}
