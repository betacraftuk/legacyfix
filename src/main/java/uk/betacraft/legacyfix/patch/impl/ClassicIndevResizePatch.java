package uk.betacraft.legacyfix.patch.impl;

import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

import java.lang.instrument.Instrumentation;

public class ClassicIndevResizePatch extends Patch {
    public ClassicIndevResizePatch() {
        super("classic-indev-resize", "Fixes resizing in Classic versions", true);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
        for (CtMethod method : minecraftClass.getDeclaredMethods()) {
            if (hasDesktopDisplayModeCall(method)) {
                throw new PatchException("Detected version past in-20100110, patch won't be applied");
            }
        }

        CtMethod runMethod = minecraftClass.getDeclaredMethod("run");

        CtField[] fields = minecraftClass.getDeclaredFields();
        CtField width = null;
        CtField height = null;
        for (int i = 0; i < fields.length - 2; i++) {
            // @formatter:off
            if (
                fields[i].getType().equals(CtClass.booleanType) &&
                fields[i + 1].getType().equals(CtClass.intType) &&
                fields[i + 2].getType().equals(CtClass.intType)
            ) {
                width = fields[i + 1];
                height = fields[i + 2];
                break;
            }
            // @formatter:on
        }

        if (width == null || height == null) {
            throw new PatchException("Game width / height fields were not found");
        }

        final CtField screenField = findScreen();
        final CtMethod initMethod = findInitMethod(screenField);

        patchPauseScreen(inst);

        final String widthFieldName = width.getName();
        final String heightFieldName = height.getName();
        final String screenFieldName = screenField == null ? null : screenField.getName();

        if (minecraftClass.isFrozen()) {
            minecraftClass.defrost();
        }

        runMethod.instrument(new ExprEditor() {
            final String thisWidth = "this." + widthFieldName;
            final String thisHeight = "this." + heightFieldName;
            final String thisScreen = screenFieldName != null ? "this." + screenFieldName : "";

            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getClassName().equals("org.lwjgl.opengl.Display") && m.getMethodName().equals("isCloseRequested")) {
                    // @formatter:off
                    m.replace(
                        "{ " +
                        "    int lastWidth = " + thisWidth + ";" +
                        "    int lastHeight = " + thisHeight + ";" +
                        "    " + thisWidth + " = org.lwjgl.opengl.Display.getWidth();" +
                        "    " + thisHeight + " = org.lwjgl.opengl.Display.getHeight();" +
                        (screenFieldName != null && initMethod != null ?
                        "    if (" + thisScreen + " != null && (" + thisWidth + " != lastWidth || " + thisHeight + " != lastHeight)) {" +
                        "        " + thisScreen + "." + initMethod.getName() + "(this, " + thisWidth + " * 240 /" + thisHeight + ", " + thisHeight + " * 240 /" + thisHeight + ");" +
                        "    }" : ""
                        ) +
                        "    $_ = $proceed($$);" +
                        "}"
                    );
                    // @formatter:on
                }
            }
        });

        patchHud(inst, findHudField(), width, height);

        this.redefineClass(inst, minecraftClass);
    }

    private boolean hasDesktopDisplayModeCall(CtMethod method) {
        try {
            CodeAttribute codeAttribute = method.getMethodInfo().getCodeAttribute();
            CodeIterator codeIterator = codeAttribute.iterator();
            ConstPool cp = method.getMethodInfo().getConstPool();

            while (codeIterator.hasNext()) {
                int pos = codeIterator.next();

                if (codeIterator.byteAt(pos) != Opcode.IFEQ ||
                    codeIterator.byteAt(pos + 3) != Opcode.INVOKESTATIC ||
                    codeIterator.byteAt(pos + 6) != Opcode.INVOKESTATIC ||
                    codeIterator.byteAt(pos + 9) != Opcode.ALOAD_0
                ) {
                    continue;
                }

                String refName = cp.getMethodrefName(codeIterator.u16bitAt(pos + 4));
                if ("getDesktopDisplayMode".equals(refName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private CtField findScreen() {
        try {
            CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
            CtField[] mcFields = minecraftClass.getDeclaredFields();

            CtField screenField = null;
            for (CtField curr : mcFields) {
                if (curr.getType().isPrimitive()) {
                    continue;
                }

                CtClass candidate = curr.getType();

                int countMinecraft = 0, countInt = 0;
                for (CtField field : candidate.getDeclaredFields()) {
                    if (!Modifier.isProtected(field.getModifiers())) {
                        continue;
                    }

                    if (field.getType().equals(minecraftClass)) {
                        countMinecraft++;
                    } else if (field.getType().equals(CtClass.intType)) {
                        countInt++;
                    }
                }

                if (countMinecraft == 1 && countInt == 2) {
                    screenField = curr;
                    LFLogger.debug("Found Screen class: " + candidate.getName());
                    LFLogger.debug("Found Screen field: " + screenField.getName());
                    break;
                }
            }

            return screenField;
        } catch (Exception e) {
            return null;
        }
    }

    private CtMethod findInitMethod(CtField screenField) {
        try {
            if (screenField == null) {
                return null;
            }

            CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
            CtClass screenClass = screenField.getType();
            for (CtMethod method : screenClass.getDeclaredMethods()) {
                CtClass[] parameters = method.getParameterTypes();
                if (parameters.length != 3) {
                    continue;
                }

                if (parameters[0] == minecraftClass && parameters[1] == CtClass.intType && parameters[2] == CtClass.intType) {
                    LFLogger.debug("Found init method: " + method.getName());
                    return method;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void patchPauseScreen(Instrumentation inst) {
        try {
            CtClass pauseScreen = pool.get("com.mojang.minecraft.gui.PauseScreen");
            CtField buttonsField = pauseScreen.getDeclaredField("buttons");
            CtMethod initMethod = pauseScreen.getDeclaredMethod("init", null);
            if (pauseScreen.isFrozen()) {
                pauseScreen.defrost();
            }

            initMethod.insertBefore("this." + buttonsField.getName() + " = new java.util.ArrayList();");
            this.redefineClass(inst, pauseScreen);
        } catch (Exception ignored) {
        }
    }

    private CtField findHudField() {
        try {
            CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
            CtMethod runMethod = minecraftClass.getDeclaredMethod("run");
            CodeAttribute codeAttribute = runMethod.getMethodInfo().getCodeAttribute();
            CodeIterator codeIterator = codeAttribute.iterator();
            ConstPool cp = runMethod.getMethodInfo().getConstPool();

            while (codeIterator.hasNext()) {
                int pos = codeIterator.next();
                int opcode = codeIterator.byteAt(pos);

                if (opcode != Opcode.LDC) {
                    continue;
                }
                int ldcIndex = codeIterator.byteAt(pos + 1);
                if (cp.getTag(ldcIndex) != 8) {
                    continue; // non-string LDC
                }
                if (!"Post startup".equals(cp.getStringInfo(ldcIndex))) {
                    continue;
                }

                // INVOKESTATIC
                int posInvoke = pos + 2;
                if (!codeIterator.hasNext() || codeIterator.byteAt(posInvoke) != Opcode.INVOKESTATIC) {
                    continue;
                }

                // ALOAD with index 4
                int posAload = posInvoke + 3;
                if (!codeIterator.hasNext() || codeIterator.byteAt(posAload) != Opcode.ALOAD) {
                    continue;
                }
                int aloadIndex = codeIterator.byteAt(posAload + 1);
                if (aloadIndex != 4) {
                    continue;
                }

                // NEW immediately after ALOAD
                int posNew = posAload + 2;
                if (!codeIterator.hasNext() || codeIterator.byteAt(posNew) != Opcode.NEW) {
                    continue;
                }
                int newIndex = codeIterator.u16bitAt(posNew + 1);
                String newClassName = cp.getClassInfo(newIndex);
                String normalizedNewClass = newClassName.replace('/', '.');

                // Scan for any PUTFIELD corresponding to the new call.
                while (codeIterator.hasNext()) {
                    int currPos = codeIterator.next();
                    if (codeIterator.byteAt(currPos) == Opcode.PUTFIELD) {
                        int fieldIndex = codeIterator.u16bitAt(currPos + 1);

                        String fieldDescriptor = cp.getFieldrefType(fieldIndex);
                        String fieldType;
                        if (fieldDescriptor.startsWith("L") && fieldDescriptor.endsWith(";")) {
                            fieldType = fieldDescriptor.substring(1, fieldDescriptor.length() - 1);
                        } else {
                            fieldType = fieldDescriptor;
                        }

                        String normalizedFieldType = fieldType.replace('/', '.');
                        if (normalizedNewClass.equals(normalizedFieldType)) {
                            String fieldName = cp.getFieldrefName(fieldIndex);

                            LFLogger.debug("Found field HUD: " + fieldName);
                            return minecraftClass.getDeclaredField(fieldName);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void patchHud(Instrumentation inst, CtField hudField, CtField widthField, CtField heightField) {
        if (hudField == null) {
            return;
        }

        try {
            CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
            CtClass hudClass = hudField.getType();
            CtMethod renderMethod = null;
            for (CtMethod m : hudClass.getDeclaredMethods()) {
                // @formatter:off
                if (
                    Modifier.isPublic(m.getModifiers()) &&
                    Modifier.isFinal(m.getModifiers()) &&
                    m.getReturnType().equals(CtClass.voidType)
                ) {
                    CtClass[] params = m.getParameterTypes();
                    if (
                        params.length == 0
                        ||
                        (params.length == 3
                        && params[0].equals(CtClass.booleanType)
                        && params[1].equals(CtClass.intType)
                        && params[2].equals(CtClass.intType))
                        ||
                        (params.length == 4
                        && params[0].equals(CtClass.floatType)
                        && params[1].equals(CtClass.booleanType)
                        && params[2].equals(CtClass.intType)
                        && params[3].equals(CtClass.intType))
                    ) {
                        renderMethod = m;
                        break;
                    }
                }
                // @formatter:on
            }

            if (renderMethod == null) {
                return;
            }
            LFLogger.debug("Found hud method: " + renderMethod.getSignature());

            CtField minecraft = null;
            CtField width = null;
            CtField height = null;

            for (CtField field : hudClass.getDeclaredFields()) {
                if (field.getType().equals(minecraftClass)) {
                    minecraft = field;
                }

                if (field.getType().equals(CtClass.intType)) {
                    if (width == null) {
                        width = field;
                    } else if (height == null) {
                        height = field;
                    }
                }
            }

            if (minecraft == null || width == null || height == null) {
                return;
            }

            if (hudClass.isFrozen()) {
                hudClass.defrost();
            }

            String minecraftWidth = minecraft.getName() + "." + widthField.getName();
            String minecraftHeight = minecraft.getName() + "." + heightField.getName();

            // @formatter:off
            renderMethod.insertBefore(
                "$0." + width.getName() + " = $0." + minecraftWidth + " * 240 / " + minecraftHeight + ";" +
                "$0." + height.getName() + " = $0." + minecraftHeight + " * 240 / " + minecraftHeight + ";"
            );
            // @formatter:on

            this.redefineClass(inst, hudClass);
        } catch (Exception e) {
            LFLogger.error("patchHud", e);
        }
    }

    @Override
    public boolean shouldApply() {
        return super.shouldApply() &&
            (pool.getOrNull("com.mojang.minecraft.MinecraftApplet") != null ||
                pool.getOrNull("net.minecraft.client.MinecraftApplet") != null);
    }
}
