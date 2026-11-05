package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.Opcode;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

/**
 * Makes Forge's older ASM not cry
 */
public class AsmClassVersionPatch extends Patch {
    public AsmClassVersionPatch() {
        super("asm-class-version", "Makes old ASM versions read class files of newer Java versions", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (!name.endsWith("objectweb.asm.ClassReader")) {
                    return null;
                }

                CtClass ctClass = ctFromBytes(name, bytecode);
                try {
                    boolean modified = false;
                    for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
                        if (!constructor.getSignature().startsWith("([BI")) {
                            continue;
                        }

                        int maxVersion = findMaxVersion(constructor);
                        if (maxVersion < 0) {
                            continue;
                        }

                        constructor.insertBeforeBody("" +
                            "if ($1 != null && $1.length > $2 + 7" +
                            "    && ((($1[$2 + 6] & 0xFF) << 8) | ($1[$2 + 7] & 0xFF)) > " + maxVersion + ") {" +
                            "    byte[] lowered = new byte[$1.length];" +
                            "    System.arraycopy($1, 0, lowered, 0, $1.length);" +
                            "    lowered[$2 + 6] = (byte) " + (maxVersion >> 8) + ";" +
                            "    lowered[$2 + 7] = (byte) " + (maxVersion & 0xFF) + ";" +
                            "    $1 = lowered;" +
                            "}"
                        );
                        modified = true;
                    }

                    if (!modified) {
                        return null;
                    }

                    Logger.debug("asm-class-version", "Made " + name + " accept newer class files");
                    return ctClass.toBytecode();
                } finally {
                    ctClass.detach();
                }
            }
        });
    }

    private static int findMaxVersion(CtConstructor constructor) throws Exception {
        CodeAttribute code = constructor.getMethodInfo().getCodeAttribute();
        if (code == null) {
            return -1;
        }

        CodeIterator it = code.iterator();
        int previous = -1;
        int previousOp = -1;
        while (it.hasNext()) {
            int pos = it.next();
            int op = it.byteAt(pos);

            if (op == Opcode.IF_ICMPLE && previous >= 0) {
                if (previousOp == Opcode.BIPUSH) {
                    return it.byteAt(previous + 1);
                }
                if (previousOp == Opcode.SIPUSH) {
                    return it.s16bitAt(previous + 1);
                }
            }

            previous = pos;
            previousOp = op;
        }
        return -1;
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9;
    }
}
