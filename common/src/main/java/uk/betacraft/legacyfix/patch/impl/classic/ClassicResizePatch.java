package uk.betacraft.legacyfix.patch.impl.classic;

import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.*;

public class ClassicResizePatch extends Patch {
    public ClassicResizePatch() {
        super("classic-resize", "Fixes resizing in Classic versions", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        final String minecraftClassName = GameClasses.findMinecraftClass(patchPool);
        if (minecraftClassName == null) {
            throw new PatchException("No minecraft class found");
        }

        final CtClass minecraftClass = patchPool.getRawClass(minecraftClassName);
        if (minecraftClass == null) {
            throw new PatchException("No minecraft class found");
        }

        for (CtMethod method : minecraftClass.getDeclaredMethods()) {
            if (hasDesktopDisplayModeCall(method)) {
                throw new PatchUnapplicableException("Detected version past in-20100110");
            }
        }

        CtField[] fields = minecraftClass.getDeclaredFields();
        CtField width = null;
        CtField height = null;
        for (int i = 0; i < fields.length - 2; i++) {
            if (fields[i].getType().equals(CtClass.booleanType)
                && fields[i + 1].getType().equals(CtClass.intType)
                && fields[i + 2].getType().equals(CtClass.intType)
            ) {
                width = fields[i + 1];
                height = fields[i + 2];
                break;
            }
        }

        if (width == null || height == null) {
            throw new PatchException("Game width / height fields were not found");
        }

        final CtField screenField = findScreen(minecraftClass);
        final CtMethod initMethod = findInitMethod(minecraftClass, screenField);
        final CtField hudField = findHudField(minecraftClass);

        final String widthFieldName = width.getName();
        final String heightFieldName = height.getName();
        final String screenFieldName = screenField == null ? null : screenField.getName();
        final String initMethodName = initMethod == null ? null : initMethod.getName();

        patchPool.addCtTransformer(minecraftClassName, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod runMethod = ctClass.getDeclaredMethod("run");

                runMethod.instrument(new ExprEditor() {
                    final String thisWidth = "this." + widthFieldName;
                    final String thisHeight = "this." + heightFieldName;
                    final String thisScreen = screenFieldName != null ? "this." + screenFieldName : "";

                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals("org.lwjgl.opengl.Display")
                            && m.getMethodName().equals("update")
                        ) {
                            m.replace("" +
                                "{" +
                                "   int lastWidth = " + thisWidth + ";" +
                                "   int lastHeight = " + thisHeight + ";" +
                                    thisWidth + " = org.lwjgl.opengl.Display.getWidth();" +
                                    thisHeight + " = org.lwjgl.opengl.Display.getHeight();" +
                                (screenFieldName != null && initMethodName != null
                                    ? "if (" + thisScreen + " != null && (" + thisWidth + " != lastWidth || " + thisHeight + " != lastHeight)) {" +
                                      "    " + thisScreen + "." + initMethodName + "(this, " + thisWidth + " * 240 / " + thisHeight + ", 240);" +
                                      "}"
                                    : ""
                                ) +
                                    "$_ = $proceed($$);" +
                                "}"
                            );
                        }
                    }
                });
            }
        });

        patchPool.addCtTransformer("com.mojang.minecraft.gui.PauseScreen", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtField buttonsField = ctClass.getDeclaredField("buttons");
                CtMethod initMethod = ctClass.getDeclaredMethod("init", null);

                if (ctClass.isFrozen()) {
                    ctClass.defrost();
                }

                initMethod.insertBefore("this." + buttonsField.getName() + " = new java.util.ArrayList();");
            }
        });

        if (hudField != null) {
            final String hudClassName = hudField.getType().getName();
            final CtField finalWidth = width;
            final CtField finalHeight = height;

            patchPool.addCtTransformer(hudClassName, new CtTransformer() {
                public void transform(CtClass hudClass) {
                    patchHud(hudClass, minecraftClass, finalWidth, finalHeight);
                }
            });
        }
    }

    private boolean hasDesktopDisplayModeCall(CtMethod method) {
        try {
            CodeAttribute codeAttribute = method.getMethodInfo().getCodeAttribute();
            CodeIterator codeIterator = codeAttribute.iterator();
            ConstPool cp = method.getMethodInfo().getConstPool();

            while (codeIterator.hasNext()) {
                int pos = codeIterator.next();

                if (codeIterator.byteAt(pos) != Opcode.IFEQ
                    || codeIterator.byteAt(pos + 3) != Opcode.INVOKESTATIC
                    || codeIterator.byteAt(pos + 6) != Opcode.INVOKESTATIC
                    || codeIterator.byteAt(pos + 9) != Opcode.ALOAD_0
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

    private CtField findScreen(CtClass minecraftClass) {
        try {
            for (CtField curr : minecraftClass.getDeclaredFields()) {
                if (curr.getType().isPrimitive()) continue;

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
                    Logger.debug("Found Screen class: " + candidate.getName());
                    Logger.debug("Found Screen field: " + curr.getName());
                    return curr;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private CtMethod findInitMethod(CtClass minecraftClass, CtField screenField) {
        try {
            if (screenField == null) return null;

            CtClass screenClass = screenField.getType();
            for (CtMethod method : screenClass.getDeclaredMethods()) {
                CtClass[] parameters = method.getParameterTypes();
                if (parameters.length == 3
                    && parameters[0].equals(minecraftClass)
                    && parameters[1] == CtClass.intType
                    && parameters[2] == CtClass.intType
                ) {
                    Logger.debug("Found init method: " + method.getName());
                    return method;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private CtField findHudField(CtClass minecraftClass) {
        try {
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
                    continue;
                }

                if (!"Post startup".equals(cp.getStringInfo(ldcIndex))) {
                    continue;
                }

                int posInvoke = pos + 2;
                if (!codeIterator.hasNext() || codeIterator.byteAt(posInvoke) != Opcode.INVOKESTATIC) {
                    continue;
                }

                int posAload = posInvoke + 3;
                if (!codeIterator.hasNext() || codeIterator.byteAt(posAload) != Opcode.ALOAD) {
                    continue;
                }

                int aloadIndex = codeIterator.byteAt(posAload + 1);
                if (aloadIndex != 4) {
                    continue;
                }

                int posNew = posAload + 2;
                if (!codeIterator.hasNext() || codeIterator.byteAt(posNew) != Opcode.NEW) {
                    continue;
                }

                int newIndex = codeIterator.u16bitAt(posNew + 1);
                String newClassName = cp.getClassInfo(newIndex);
                String normalizedNewClass = newClassName.replace('/', '.');

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

                            Logger.debug("Found HUD field: " + fieldName);
                            return minecraftClass.getDeclaredField(fieldName);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void patchHud(CtClass hudClass, CtClass minecraftClass, CtField widthField, CtField heightField) {
        try {
            CtMethod renderMethod = null;
            for (CtMethod m : hudClass.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers())
                    && Modifier.isFinal(m.getModifiers())
                    && m.getReturnType().equals(CtClass.voidType)) {
                    CtClass[] params = m.getParameterTypes();
                    if (params.length == 0
                        || (params.length == 3
                        && params[0].equals(CtClass.booleanType)
                        && params[1].equals(CtClass.intType)
                        && params[2].equals(CtClass.intType))
                        || (params.length == 4
                        && params[0].equals(CtClass.floatType)
                        && params[1].equals(CtClass.booleanType)
                        && params[2].equals(CtClass.intType)
                        && params[3].equals(CtClass.intType))) {
                        renderMethod = m;
                        break;
                    }
                }
            }

            if (renderMethod == null) {
                return;
            }

            CtField minecraft = null;
            CtField width = null;
            CtField height = null;

            for (CtField field : hudClass.getDeclaredFields()) {
                if (field.getType().equals(minecraftClass)) {
                    minecraft = field;
                } else if (field.getType().equals(CtClass.intType)) {
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

            renderMethod.insertBefore("" +
                "$0." + width.getName() + " = $0." + minecraftWidth + " * 240 / " + minecraftHeight + ";" +
                "$0." + height.getName() + " = $0." + minecraftHeight + " * 240 / " + minecraftHeight + ";"
            );
        } catch (Exception e) {
            Logger.error("patchHud", e);
        }
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) &&
            (
                patchPool.getRawClass("com.mojang.minecraft.MinecraftApplet") != null ||
                patchPool.getRawClass("net.minecraft.client.MinecraftApplet") != null
            );
    }
}