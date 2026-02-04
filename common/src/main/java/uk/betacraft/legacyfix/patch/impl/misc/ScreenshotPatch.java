package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class ScreenshotPatch extends Patch {
    public ScreenshotPatch() {
        super("screenshot", "Fixes screenshots after maximizing the game window in a1.2.0-12w23b", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        CtMethod takeMethod = this.findScreenshotMethod(patchPool);
        if (takeMethod == null) {
            throw new PatchException("Not applicable");
        }

        CtClass screenshotClass = takeMethod.getDeclaringClass();
        if (screenshotClass.isFrozen()) {
            screenshotClass.defrost();
        }

        CtField pixelsField = null;
        for (CtField field : screenshotClass.getDeclaredFields()) {
            if (!"java.nio.ByteBuffer".equals(field.getType().getName())) {
                continue;
            }

            pixelsField = field;
        }

        if (pixelsField == null) {
            throw new PatchException("No Screenshot.pixels found");
        }

        String pixelsRef = screenshotClass.getName() + "." + pixelsField.getName();
        takeMethod.insertBefore("" +
            "try {" +
            "    if (" + pixelsRef + " == null || " + pixelsRef + ".capacity() != $2 * $3 * 3) {" +
            "        " + pixelsRef + " = org.lwjgl.BufferUtils.createByteBuffer($2 * $3 * 3);" +
            "    }" +
            "} catch (Throwable t) { t.printStackTrace(); }"
        );

        patchPool.patchClass(screenshotClass);
    }

    private CtMethod findScreenshotMethod(PatchPool patchPool) {
        try {
            CtClass fileClass = patchPool.getClass("java.io.File");
            CtClass minecraftClass = GameClasses.findMinecraftClass(patchPool);
            if (minecraftClass.isFrozen()) {
                minecraftClass.defrost();
            }

            for (CtMethod candidateMethod : minecraftClass.getDeclaredMethods()) {
                CodeAttribute codeAttribute = candidateMethod.getMethodInfo().getCodeAttribute();
                if (codeAttribute == null) {
                    continue;
                }

                CodeIterator codeIterator = codeAttribute.iterator();
                ConstPool cp = candidateMethod.getMethodInfo().getConstPool();

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

                    String refName = cp.getMethodrefName(codeIterator.u16bitAt(pos + 3));
                    String refClassName = cp.getMethodrefClassName(codeIterator.u16bitAt(pos + 3));
                    if (!"isKeyDown".equals(refName)) {
                        break;
                    }

                    if (!"org.lwjgl.input.Keyboard".equals(refClassName)) {
                        break;
                    }

                    for (int i = 0; i < 7; i++) {
                        codeIterator.next();
                    }

                    while (codeIterator.hasNext()) {
                        pos = codeIterator.next();
                        if (codeIterator.byteAt(pos) != Opcode.INVOKESTATIC) {
                            continue;
                        }

                        String refType = cp.getMethodrefType(codeIterator.u16bitAt(pos + 1));
                        refName = cp.getMethodrefName(codeIterator.u16bitAt(pos + 1));
                        refClassName = cp.getMethodrefClassName(codeIterator.u16bitAt(pos + 1));

                        if (!"(Ljava/io/File;II)Ljava/lang/String;".equals(refType)) {
                            break;
                        }

                        Logger.debug("screenshot", "Found Screenshot class: " + refClassName);
                        Logger.debug("screenshot", "Found Screenshot.take method: " + refName);

                        return patchPool.getClass(refClassName).getDeclaredMethod(refName, new CtClass[]{fileClass, Patch.CT_INT, Patch.CT_INT});
                    }
                }
            }
        } catch (Exception e) {
            Logger.error(this, e);
        }

        return null;
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && patchPool.getClass("net.minecraft.client.MinecraftApplet") != null;
    }
}
