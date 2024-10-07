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
import javassist.expr.NewExpr;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;
import uk.betacraft.legacyfix.util.IconUtils;

public class DeAwtPatch extends Patch {
    private String canvasClassName;
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

        CtClass appletClass = PatchHelper.findMinecraftAppletClass(pool);
        if (appletClass == null) {
            throw new PatchException("No applet class could be found");
        }
        final String appletClassName = appletClass.getName();

        CtField minecraftField = PatchHelper.findMinecraftField(pool);
        CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
        if (minecraftField == null || minecraftClass == null) {
            throw new PatchException("No main Minecraft field could be found");
        }

        CtField appletModeField = PatchHelper.findAppletModeField(pool);
        CtMethod initMethod = appletClass.getDeclaredMethod("init");

        // @formatter:off
        initMethod.insertAfter(
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
            "$0." + minecraftField.getName() + "." + appletModeField.getName() + " = false;"
        );
        // @formatter:on

        // Take the canvas class name to later edit out its removeNotify() method
        // The method relies on AWT/Swing components, so it has to be hijacked
        initMethod.instrument(new ExprEditor() {
            public void edit(NewExpr m) {
                try {
                    if (m.getConstructor().getLongName().contains("(" + appletClassName + ")") &&
                            m.getSignature().equals("(L" + appletClassName.replace(".", "/") + ";)V")) {
                        canvasClassName = m.getClassName();
                        LFLogger.info("Found Canvas class name: " + canvasClassName);
                    }
                } catch (NotFoundException e) {
                    DeAwtPatch.this.thrown = e;
                }
            }
        });

        if (this.thrown != null) {
            throw this.thrown;
        }

        inst.redefineClasses(new ClassDefinition(Class.forName(appletClassName), appletClass.toBytecode()));


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
                    LFLogger.error("DeAwt.minecraftClass", e);
                }
            }
        });

        CtConstructor minecraftConstructor = minecraftClass.getConstructors()[0];

        // Nullify Canvas/MinecraftApplet
        // The typical formula of a Minecraft constructor goes like this:
        //  Component, Canvas, MinecraftApplet, int, int, boolean
        CtClass[] paramTypes = minecraftConstructor.getParameterTypes();

        for (int i = 0; i < paramTypes.length; i++) {
            String className = paramTypes[i].getName();
            // If we're at int already, it's done
            if (className.equals("int")) {
                break;
            }

            // Nullify Canvas
            if (className.equals("java.awt.Canvas") ||
                    // Only nullify MinecraftApplet if it's not a Classic version
                    // TODO: Replace this with removing the www.minecraft.net check in loadLevel()
                    ((!appletClassName.startsWith("com.mojang") && pool.getOrNull("com.a.a.a") == null)
                            && className.equals(appletClassName))) {

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

                if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
                        codeIterator.byteAt(pos + 1) != Opcode.GETFIELD ||
                        codeIterator.byteAt(pos + 4) != Opcode.IFNULL ||
                        codeIterator.byteAt(pos + 7) != Opcode.ALOAD_0) {
                    continue;
                }

                final int futurePos = pos + 8;
                // A second ALOAD appears in triggerFullscreen(), IFNE appears in the runGameLoop() method
                if (codeIterator.byteAt(futurePos) != Opcode.ALOAD_0 &&
                        codeIterator.byteAt(futurePos + 3) != Opcode.IFNE) {
                    continue;
                }

                String refType = runConstPool.getFieldrefType(codeIterator.u16bitAt(pos + 2));
                if (!"Ljava/awt/Canvas;".equals(refType))
                    continue;

                // Erase the check
                for (int i = 0; i < 7; i++) {
                    codeIterator.writeByte(Opcode.NOP, pos + i);
                }
            }
        }

        byte[] minecraftClassBytes = minecraftClass.toBytecode();
        minecraftClass.defrost();
        inst.redefineClasses(new ClassDefinition(Class.forName(minecraftClass.getName()), minecraftClassBytes));

        // deAWT Canvas
        // Stop all calls from the canvas when it gets removed
        canvasClassName = canvasClassName.replace("/", ".");
        CtClass canvasClass = pool.get(canvasClassName);
        CtMethod canvasRemoveNotifyMethod = canvasClass.getDeclaredMethod("removeNotify");
        // @formatter:off
        canvasRemoveNotifyMethod.setBody(
            "{" +
            "    super.removeNotify();" +
            "}"
        );
        // @formatter:on
        inst.redefineClasses(new ClassDefinition(Class.forName(canvasClassName), canvasClass.toBytecode()));
    }
}
