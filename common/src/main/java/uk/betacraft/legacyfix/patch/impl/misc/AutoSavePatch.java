package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.*;
import javassist.bytecode.*;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;

public class AutoSavePatch extends Patch {
    // 5 minutes by default
    private static final int SAVE_INTERVAL_TICKS = Agent.getIntSetting("lf.autosave.interval", 6000);

    public AutoSavePatch() {
        super("autosave", "Changes the interval of world autosaving to (by default) 5 minutes", true);
    }

    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addTransformer(new Transformer() {
            private boolean applied = false;

            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (this.applied) {
                    return null;
                }

                CtClass clas = ctFromBytes(bytecode);
                if (clas == null) {
                    return null;
                }

                if (name == null) {
                    name = clas.getName();
                }

                if (name.startsWith("org.lwjgl") || clas.isFrozen()) {
                    return null;
                }
                for (String ignore : Patcher.JVM_CLASS_IGNORES) {
                    if (name.startsWith(ignore)) {
                        return null;
                    }
                }

                try {
                    boolean classPatched = false;

                    for (CtMethod method : clas.getDeclaredMethods()) {
                        if (patchSaveInterval(clas, method)) {
                            classPatched = true;
                        }
                    }

                    if (classPatched) {
                        Logger.debug(AutoSavePatch.this.getId(), "Patched World class (" + clas.getName() + ") changing the save interval to " + SAVE_INTERVAL_TICKS + " ticks");
                        applied = true;
                        return clas.toBytecode();
                    }

                } catch (Throwable t) {
                    Logger.error(AutoSavePatch.this, t);
                }

                return null;
            }
        });
    }

    private boolean patchSaveInterval(CtClass clazz, CtMethod m) throws BadBytecode {
        if (patch1(clazz, m.getMethodInfo())) {
            return true;
        } else if (patch2(clazz, m.getMethodInfo())) {
            return true;
        } else return patch3(clazz, m.getMethodInfo());
    }

    /**
     * inf-20100327 to a1.0.17_02
     * 200, 100, 20, 4
     */
    private boolean patch1(CtClass clazz, MethodInfo mi) throws BadBytecode {
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) {
            return false;
        }

        ConstPool cp = mi.getConstPool();
        CodeIterator it = ca.iterator();

        while (it.hasNext()) {
            int pos = it.next();

            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.DUP || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.GETFIELD || !it.hasNext()) {
                continue;
            }

            if (!"J".equals(cp.getFieldrefType(it.u16bitAt(pos + 1)))) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCONST_1 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LADD || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.PUTFIELD || !it.hasNext()) {
                continue;
            }

            if (!"J".equals(cp.getFieldrefType(it.u16bitAt(pos + 1)))) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.GETFIELD || !it.hasNext()) {
                continue;
            }

            if (!"J".equals(cp.getFieldrefType(it.u16bitAt(pos + 1)))) {
                continue;
            }

            int ldcPos = it.next();
            int ldcOpcode =  it.byteAt(ldcPos);
            if ((ldcOpcode != Opcode.LDC && ldcOpcode != Opcode.LDC2_W) || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LREM || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCONST_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCMP || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.IFNE || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ICONST_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            int opcode = it.byteAt(pos);
            if (opcode == Opcode.ACONST_NULL && it.hasNext()) {
                pos = it.next();
                opcode = it.byteAt(pos);
                if (opcode != Opcode.INVOKEVIRTUAL && opcode != Opcode.INVOKESPECIAL) {
                    continue;
                }
            } else if (opcode != Opcode.INVOKESPECIAL) {
                continue;
            }

            String sig = cp.getMethodrefType(it.u16bitAt(pos + 1));
            if (sig == null || !sig.endsWith(")V") || !sig.contains("Z")) {
                continue;
            }

            int ldcIndex = ldcOpcode == Opcode.LDC ? it.byteAt(ldcPos + 1) : it.u16bitAt(ldcPos + 1);
            if (cp.getTag(ldcIndex) != ConstPool.CONST_Long) {
                continue;
            }

            int toErase = 2 + (ldcOpcode == Opcode.LDC2_W ? 1 : 0);
            for (int i = 0; i < toErase; i++) {
                it.writeByte(Opcode.NOP, ldcPos + i);
            }

            Bytecode code = new Bytecode(clazz.getClassFile().getConstPool());
            code.addLdc2w(SAVE_INTERVAL_TICKS);
            it.insert(ldcPos, code.get());
            ca.computeMaxStack();

            Logger.debug(this.getId(), "Patched autosave using method #1 in " + clazz.getName() + "." + mi.getName());
            return true;
        }
        return false;
    }

    /**
     * a1.0.17_03 to b1.2_02
     * 40
     */
    private boolean patch2(CtClass clazz, MethodInfo mi) throws BadBytecode {
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) {
            return false;
        }

        ConstPool cp = mi.getConstPool();
        CodeIterator it = ca.iterator();

        while (it.hasNext()) {
            int pos = it.next();
            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.DUP || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.GETFIELD || !it.hasNext()) {
                continue;
            }

            if (!"J".equals(cp.getFieldrefType(it.u16bitAt(pos + 1)))) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCONST_1 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LADD || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.PUTFIELD || !it.hasNext()) {
                continue;
            }

            if (!"J".equals(cp.getFieldrefType(it.u16bitAt(pos + 1)))) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.GETFIELD || !it.hasNext()) {
                continue;
            }

            if (!"J".equals(cp.getFieldrefType(it.u16bitAt(pos + 1)))) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            int getFieldPos = it.next();
            if (it.byteAt(getFieldPos) != Opcode.GETFIELD || !it.hasNext()) {
                continue;
            }

            int fieldIndex = it.u16bitAt(getFieldPos + 1);
            if (!"I".equals(cp.getFieldrefType(fieldIndex))) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.I2L || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LREM || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCONST_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCMP || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.IFNE || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ICONST_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ACONST_NULL || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.INVOKEVIRTUAL) {
                continue;
            }

            String sig = cp.getMethodrefType(it.u16bitAt(pos + 1));
            if (sig == null || !sig.endsWith(")V") || !sig.contains("Z")) {
                continue;
            }

            String lastSaveTimeFieldName = cp.getFieldrefName(fieldIndex);

            return patchConstructors(clazz, lastSaveTimeFieldName, 2);
        }
        return false;
    }

    /**
     * b1.3 to 12w17a
     * 40
     */
    private boolean patch3(CtClass clazz, MethodInfo mi) throws BadBytecode {
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) {
            return false;
        }

        ConstPool cp = mi.getConstPool();
        CodeIterator it = ca.iterator();

        while (it.hasNext()) {
            int pos = it.next();
            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.GETFIELD || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.INVOKEVIRTUAL || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCONST_1 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LADD || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LSTORE_2 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LLOAD_2 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            int getFieldPos = it.next();
            if (it.byteAt(getFieldPos) != Opcode.GETFIELD || !it.hasNext()) {
                continue;
            }

            int fieldIndex = it.u16bitAt(getFieldPos + 1);
            if (!"I".equals(cp.getFieldrefType(fieldIndex))) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.I2L || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LREM || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCONST_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.LCMP || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.IFNE || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) == Opcode.LDC && it.hasNext()) {
                int ldcIndex = it.byteAt(pos + 1);
                if (cp.getTag(ldcIndex) != ConstPool.CONST_String) {
                    continue;
                }

                String ldcString = cp.getStringInfo(ldcIndex);
                if (!"save".equals(ldcString)) {
                    continue;
                }

                pos = it.next();
                if (it.byteAt(pos) == Opcode.INVOKESTATIC && it.hasNext()) {
                    pos = it.next();
                } else {
                    continue;
                }
            }

            if (it.byteAt(pos) != Opcode.ALOAD_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ICONST_0 || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.ACONST_NULL || !it.hasNext()) {
                continue;
            }

            pos = it.next();
            if (it.byteAt(pos) != Opcode.INVOKEVIRTUAL) {
                continue;
            }

            String sig = cp.getMethodrefType(it.u16bitAt(pos + 1));
            if (sig == null || !sig.endsWith(")V") || !sig.contains("Z")) {
                continue;
            }

            String lastSaveTimeFieldName = cp.getFieldrefName(fieldIndex);

            return patchConstructors(clazz, lastSaveTimeFieldName, 3);
        }
        return false;
    }

    private boolean patchConstructors(CtClass clazz, String lastSaveTimeFieldName, int methodType) throws BadBytecode {
        boolean constrPatched = false;

        for (CtConstructor constr : clazz.getDeclaredConstructors()) {
            MethodInfo cmi = constr.getMethodInfo();
            CodeAttribute cca = cmi.getCodeAttribute();
            if (cca == null) {
                return false;
            }

            ConstPool ccp = cmi.getConstPool();
            CodeIterator cit = cca.iterator();

            while (cit.hasNext()) {
                int pos = cit.next();
                if (cit.byteAt(pos) != Opcode.ALOAD_0 || !cit.hasNext()) {
                    continue;
                }

                int biPushPos = cit.next();
                if (cit.byteAt(biPushPos) != Opcode.BIPUSH || !cit.hasNext()) {
                    continue;
                }

                int bytePushed = cit.byteAt(biPushPos + 1);
                if (bytePushed != 40) {
                    continue;
                }

                pos = cit.next();
                if (cit.byteAt(pos) != Opcode.PUTFIELD) {
                    continue;
                }

                int fieldIndex = cit.u16bitAt(pos + 1);
                if (!"I".equals(ccp.getFieldrefType(fieldIndex))) {
                    continue;
                }

                if (!lastSaveTimeFieldName.equals(ccp.getFieldrefName(fieldIndex))) {
                    continue;
                }

                for (int i = 0; i < 2; i++) {
                    cit.writeByte(Opcode.NOP, biPushPos + i);
                }

                Bytecode code = new Bytecode(clazz.getClassFile().getConstPool());
                code.addLdc(ccp.addIntegerInfo(SAVE_INTERVAL_TICKS));
                cit.insert(biPushPos, code.get());
                cca.computeMaxStack();

                constrPatched = true;
                Logger.debug(this.getId(), "Patched autosave using method #" + methodType + " in " + clazz.getName() + "." + cmi.getName() + cmi.getDescriptor());
            }
        }

        return constrPatched;
    }
}
