package uk.betacraft.legacyfix.patch.impl.java;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.ConstantPool;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

/**
 * ASM <5.1 can't tell a method handle points to an interface method, and writes
 * invokestatic/invokespecial handles as CONSTANT_Methodref (j9+ cries)
 */
public class InterfaceMethodRefPatch extends Patch {
    private static final int REF_INVOKE_STATIC = 6;
    private static final int REF_INVOKE_SPECIAL = 7;
    private static final int ACC_INTERFACE = 0x0200;
    private static final int JAVA_8 = 52;

    public InterfaceMethodRefPatch() {
        super("interface-methodref", "Fixes interface method handles written by old ASM for Java 9+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) {
                byte[] result = fix(bytecode);
                if (result != null) {
                    Logger.debug("interface-methodref", "Fixed interface method handles in class " + name);
                }
                return result;
            }
        });
    }

    private static byte[] fix(byte[] b) {
        ConstantPool pool = ConstantPool.parse(b);
        if (pool == null || pool.u2(6) < JAVA_8) {
            return null;
        }

        boolean isInterface = (pool.u2(pool.end()) & ACC_INTERFACE) != 0;
        int thisClass = pool.u2(pool.end() + 2);

        byte[] result = null;
        for (int i = 1; i < pool.count(); i++) {
            if (pool.tag(i) != ConstantPool.METHOD_HANDLE) {
                continue;
            }

            int off = pool.offset(i);
            int kind = b[off + 1] & 0xFF;
            if (kind != REF_INVOKE_STATIC && kind != REF_INVOKE_SPECIAL) {
                continue;
            }

            int ref = pool.u2(off + 2);
            if (pool.tag(ref) != ConstantPool.METHODREF) {
                continue;
            }

            int owner = pool.u2(pool.offset(ref) + 1);
            boolean interfaceOwner = owner == thisClass ? isInterface : isJdkInterface(pool.className(owner));
            if (!interfaceOwner) {
                continue;
            }

            if (result == null) {
                result = b.clone();
            }
            result[pool.offset(ref)] = (byte) ConstantPool.INTERFACE_METHODREF;
        }
        return result;
    }

    private static boolean isJdkInterface(String internalName) {
        if (!internalName.startsWith("java/") && !internalName.startsWith("javax/") && !internalName.startsWith("jdk/")) {
            return false;
        }

        try {
            return Class.forName(internalName.replace('/', '.'), false, null).isInterface();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9;
    }
}
