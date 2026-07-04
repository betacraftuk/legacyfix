package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.*;

public class ScreenshotPatch extends Patch {
    public ScreenshotPatch() {
        super("screenshot", "Fixes screenshots after maximizing the game window in a1.2.0-12w23b", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        final String mcClassName = GameClasses.findMinecraftClass(patchPool);
        if (mcClassName == null) {
            throw new PatchException("Minecraft class not found");
        }

        final CtClass mcClass = patchPool.getRawClass(mcClassName);
        final String[] screenshotInfo = findScreenshotMethodInfo(mcClass);

        if (screenshotInfo == null) {
            throw new PatchUnapplicableException("Screenshot method not found");
        }

        final String screenshotClassName = screenshotInfo[0];
        final String screenshotMethodName = screenshotInfo[1];
        patchPool.addCtTransformer(screenshotClassName, new CtTransformer() {
            public void transform(CtClass screenshotClass) throws Exception {
                CtField pixelsField = null;
                for (CtField field : screenshotClass.getDeclaredFields()) {
                    if ("java.nio.ByteBuffer".equals(field.getType().getName())) {
                        pixelsField = field;
                        break;
                    }
                }

                if (pixelsField == null) {
                    Logger.debug("ScreenshotPatch", "No Screenshot.pixels found in " + screenshotClass.getName());
                    return;
                }

                String pixelsRef = screenshotClass.getName() + "." + pixelsField.getName();
                CtMethod takeMethod = screenshotClass.getDeclaredMethod(screenshotMethodName);
                takeMethod.insertBefore("" +
                    "try {" +
                    "    if (" + pixelsRef + " == null || " + pixelsRef + ".capacity() != $2 * $3 * 3) {" +
                    "        " + pixelsRef + " = org.lwjgl.BufferUtils.createByteBuffer($2 * $3 * 3);" +
                    "    }" +
                    "} catch (Throwable t) { t.printStackTrace(); }"
                );
            }
        });
    }

    private String[] findScreenshotMethodInfo(CtClass minecraftClass) throws Exception {
        if (minecraftClass.isFrozen()) {
            minecraftClass.defrost();
        }

        for (CtMethod candidateMethod : minecraftClass.getDeclaredMethods()) {
            MethodInfo mi = candidateMethod.getMethodInfo();
            CodeAttribute codeAttribute = mi.getCodeAttribute();
            if (codeAttribute == null) {
                continue;
            }

            CodeIterator codeIterator = codeAttribute.iterator();
            ConstPool cp = mi.getConstPool();

            if (codeIterator.getCodeLength() <= 6) {
                continue;
            }

            while (codeIterator.hasNext()) {
                int pos = codeIterator.next();
                if (pos != 0) {
                    break;
                }

                int opcode = codeIterator.byteAt(pos);

                if (opcode != Opcode.BIPUSH &&
                    codeIterator.byteAt(pos + 2) != Opcode.INVOKESTATIC &&
                    codeIterator.byteAt(pos + 5) != Opcode.IFEQ
                ) {
                    continue;
                }

                int keyId = codeIterator.byteAt(pos + 1);
                if (keyId != 60) {
                    break;
                }

                int keyMethodIndex = codeIterator.u16bitAt(pos + 3);
                String refName = cp.getMethodrefName(keyMethodIndex);
                String refClassName = cp.getMethodrefClassName(keyMethodIndex);

                if (!"isKeyDown".equals(refName) || !"org.lwjgl.input.Keyboard".equals(refClassName)) {
                    break;
                }

                for (int i = 0; i < 7; i++) {
                    if (codeIterator.hasNext()) codeIterator.next();
                }

                while (codeIterator.hasNext()) {
                    pos = codeIterator.next();
                    if (codeIterator.byteAt(pos) != Opcode.INVOKESTATIC) {
                        continue;
                    }

                    int methodIndex = codeIterator.u16bitAt(pos + 1);
                    String refType = cp.getMethodrefType(methodIndex);
                    String foundName = cp.getMethodrefName(methodIndex);
                    String foundClassName = cp.getMethodrefClassName(methodIndex);

                    if ("(Ljava/io/File;II)Ljava/lang/String;".equals(refType)) {
                        Logger.debug("screenshot", "Found Screenshot class: " + foundClassName);
                        Logger.debug("screenshot", "Found Screenshot.take method: " + foundName);

                        return new String[] { foundClassName, foundName };
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && patchPool.getRawClass("net.minecraft.client.MinecraftApplet") != null;
    }
}