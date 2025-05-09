package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class ScreenshotPatch extends Patch {
    public ScreenshotPatch() {
        super("screenshot", "Fixes screenshotting after maximizing the game window in a1.2.0-12w23b", true);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtMethod takeMethod = this.findScreenshotMethod();
        if (takeMethod == null)
            throw new PatchException("No Screenshot.take method found, likely running version before a1.2.0");

        CtClass screenshotClass = takeMethod.getDeclaringClass();
        if (screenshotClass.isFrozen())
            screenshotClass.defrost();

        CtField pixelsField = null;
        for (CtField field : screenshotClass.getDeclaredFields()) {
            if (!"java.nio.ByteBuffer".equals(field.getType().getName()))
                continue;

            pixelsField = field;
        }

        if (pixelsField == null)
            throw new PatchException("No Screenshot.pixels field found");

        String pixelsRef = screenshotClass.getName() + "." + pixelsField.getName();

        // @formatter:off
        takeMethod.insertBefore(
            "try {" +
            "    if (" + pixelsRef + " == null || " + pixelsRef + ".capacity() != $2 * $3 * 3) {" +
            "        " + pixelsRef + " = org.lwjgl.BufferUtils.createByteBuffer($2 * $3 * 3);" +
            "    }" +
            "} catch (Throwable t) { t.printStackTrace(); }"
        );
        // @formatter:on

        inst.redefineClasses(new ClassDefinition(screenshotClass.toClass(), screenshotClass.toBytecode()));
    }

    private CtMethod findScreenshotMethod() {
        try {
            CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
            for (CtMethod candidateMethod : minecraftClass.getDeclaredMethods()) {
                CodeAttribute codeAttribute = candidateMethod.getMethodInfo().getCodeAttribute();
                if (codeAttribute == null)
                    continue;

                CodeIterator codeIterator = codeAttribute.iterator();
                ConstPool cp = candidateMethod.getMethodInfo().getConstPool();

                if (codeIterator.getCodeLength() <= 6)
                    continue;

                while (codeIterator.hasNext()) {
                    int pos = codeIterator.next();
                    if (pos != 0)
                        break;

                    int opcode = codeIterator.byteAt(pos);

                    if (opcode != Opcode.BIPUSH &&
                            codeIterator.byteAt(pos + 2) != Opcode.INVOKESTATIC &&
                            codeIterator.byteAt(pos + 5) != Opcode.IFEQ
                    ) continue;

                    int keyId = codeIterator.byteAt(pos + 1);
                    if (keyId != 60)
                        break;

                    String refName = cp.getMethodrefName(codeIterator.u16bitAt(pos + 3));
                    String refClassName = cp.getMethodrefClassName(codeIterator.u16bitAt(pos + 3));
                    if (!"isKeyDown".equals(refName))
                        break;

                    if (!"org.lwjgl.input.Keyboard".equals(refClassName))
                        break;

                    for (int i = 0; i < 7; i++) {
                        codeIterator.next();
                    }

                    while (codeIterator.hasNext()) {
                        pos = codeIterator.next();
                        if (codeIterator.byteAt(pos) != Opcode.INVOKESTATIC)
                            continue;

                        String refType = cp.getMethodrefType(codeIterator.u16bitAt(pos + 1));
                        refName = cp.getMethodrefName(codeIterator.u16bitAt(pos + 1));
                        refClassName = cp.getMethodrefClassName(codeIterator.u16bitAt(pos + 1));

                        if (!"(Ljava/io/File;II)Ljava/lang/String;".equals(refType))
                            break;

                        LFLogger.debug("screenshot", "Found Screenshot class: " + refClassName);
                        LFLogger.debug("screenshot", "Found Screenshot.take method: " + refName);

                        return pool.get(refClassName).getDeclaredMethod(refName, new CtClass[]{PatchHelper.fileClass, PatchHelper.intClass, PatchHelper.intClass});
                    }
                }
            }
        } catch (Exception e) {
            LFLogger.error(this, e);
        }

        return null;
    }

    @Override
    public boolean shouldApply() {
        return super.shouldApply() && pool.getOrNull("net.minecraft.client.MinecraftApplet") != null;
    }
}
