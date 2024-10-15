package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.*;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

/**
 * Fixes server joining for c0.0.15a and saving in early Classic
 */
public class ClassicPatch extends Patch {
    public ClassicPatch() {
        super("classicpatch", "Fixes server joining for c0.0.15a and saving in early Classic", true);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass minecraftAppletClass = PatchHelper.findMinecraftAppletClass(pool);
        if (minecraftAppletClass == null)
            throw new PatchException("No applet class could be found");

        if (minecraftAppletClass.isFrozen())
            minecraftAppletClass.defrost();

        CtMethod initMethod = minecraftAppletClass.getDeclaredMethod("init");

        ConstPool initConstPool = initMethod.getMethodInfo().getConstPool();

        CodeAttribute codeAttribute = initMethod.getMethodInfo().getCodeAttribute();
        CodeIterator codeIterator = codeAttribute.iterator();

        while (codeIterator.hasNext()) {
            int pos = codeIterator.next();

            if (patch15aServerJoin(initMethod, codeIterator, initConstPool, pos)) {
                CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);

                inst.redefineClasses(new ClassDefinition(Class.forName(minecraftClass.getName()), minecraftClass.toBytecode()));
            }

            patchMinecraftUriPort(codeIterator, initConstPool, pos);
        }

        inst.redefineClasses(new ClassDefinition(Class.forName(minecraftAppletClass.getName()), minecraftAppletClass.toBytecode()));
    }

    private boolean patch15aServerJoin(CtMethod initMethod, CodeIterator codeIterator, ConstPool constPool, int pos) throws BadBytecode, CannotCompileException, NotFoundException {
        if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
                codeIterator.byteAt(pos + 1) != Opcode.GETFIELD ||
                codeIterator.byteAt(pos + 4) != Opcode.LDC ||
                codeIterator.byteAt(pos + 6) != Opcode.SIPUSH ||
                codeIterator.byteAt(pos + 9) != Opcode.INVOKEVIRTUAL) {
            return false;
        }

        int ldcPos = codeIterator.byteAt(pos + 5);
        if (!PatchHelper.isString(constPool, ldcPos))
            return false;

        String defaultString = constPool.getStringInfo(ldcPos);
        int defaultPort = codeIterator.s16bitAt(pos + 7);

        if (!"79.136.77.240".equals(defaultString))
            return false;

        if (defaultPort != 5565)
            return false;

        int methodRef = codeIterator.u16bitAt(pos + 10);
        String setServerMethodSign = constPool.getMethodrefType(methodRef);
        String setServerMethodName = constPool.getMethodrefName(methodRef);
        String minecraftFieldName = constPool.getFieldrefName(codeIterator.u16bitAt(pos + 2));
        if (!"(Ljava/lang/String;I)V".equals(setServerMethodSign))
            return false;

        // Erase setServer call
        for (int i = 0; i < 12; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + i);
        }

        // Add setServer at the end of init(), so that username gets read first
        // @formatter:off
        initMethod.insertAfter(
            "if ($0.getParameter(\"server\") != null && $0.getParameter(\"port\") != null) {" +
            "    $0." + minecraftFieldName + "." + setServerMethodName + "($0.getParameter(\"server\"), Integer.parseInt($0.getParameter(\"port\")));" +
            "}"
        );
        // @formatter:on

        CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);
        CtMethod setServerMethod = minecraftClass.getDeclaredMethod(setServerMethodName,
                new CtClass[]{PatchHelper.stringClass, PatchHelper.intClass});

        replaceHardcodedPort(setServerMethod);

        if (LegacyFixAgent.isDebug()) {
            LFLogger.info("classicpatch", "Patched c0.0.15a's init()");
        }

        return true;
    }

    /**
     * Server port is hardcoded to 5565 while instantiating ConnectionManager
     */
    private void replaceHardcodedPort(CtMethod setServerMethod) throws BadBytecode {
        CodeAttribute codeAttribute = setServerMethod.getMethodInfo().getCodeAttribute();
        CodeIterator codeIterator = codeAttribute.iterator();

        while (codeIterator.hasNext()) {
            int pos = codeIterator.next();

            if (codeIterator.byteAt(pos) != Opcode.SIPUSH)
                continue;

            if (codeIterator.s16bitAt(pos + 1) != 5565)
                continue;

            codeIterator.writeByte(Opcode.ILOAD_2, pos);
            codeIterator.writeByte(Opcode.NOP, pos + 1);
            codeIterator.writeByte(Opcode.NOP, pos + 2);
        }
    }

    /**
     * Patch for negative port number during saving
     */
    private void patchMinecraftUriPort(CodeIterator codeIterator, ConstPool constPool, int pos) {
        if (codeIterator.byteAt(pos) != Opcode.INVOKESPECIAL ||
                codeIterator.byteAt(pos + 3) != Opcode.ALOAD_0 ||
                codeIterator.byteAt(pos + 4) != Opcode.INVOKEVIRTUAL ||
                codeIterator.byteAt(pos + 7) != Opcode.INVOKEVIRTUAL ||
                codeIterator.byteAt(pos + 10) != Opcode.INVOKEVIRTUAL ||
                codeIterator.byteAt(pos + 13) != Opcode.LDC ||
                codeIterator.byteAt(pos + 15) != Opcode.INVOKEVIRTUAL ||
                codeIterator.byteAt(pos + 18) != Opcode.ALOAD_0 ||
                codeIterator.byteAt(pos + 19) != Opcode.INVOKEVIRTUAL ||
                codeIterator.byteAt(pos + 22) != Opcode.INVOKEVIRTUAL ||
                codeIterator.byteAt(pos + 25) != Opcode.INVOKEVIRTUAL) {
            return;
        }

        int ldcPos = codeIterator.byteAt(pos + 14);
        if (!PatchHelper.isString(constPool, ldcPos))
            return;

        String separatorString = constPool.getStringInfo(ldcPos);
        if (!":".equals(separatorString))
            return;

        String appendStringType = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
        String appendIntType = "(I)Ljava/lang/StringBuilder;";

        if (!appendStringType.equals(constPool.getMethodrefType(codeIterator.u16bitAt(pos + 11))) ||
                !appendStringType.equals(constPool.getMethodrefType(codeIterator.u16bitAt(pos + 16))) ||
                !appendIntType.equals(constPool.getMethodrefType(codeIterator.u16bitAt(pos + 26))))
            return;

        String getDocumentBaseType = "()Ljava/net/URL;";
        if (!getDocumentBaseType.equals(constPool.getMethodrefType(codeIterator.u16bitAt(pos + 5))) ||
                !"getDocumentBase".equals(constPool.getMethodrefName(codeIterator.u16bitAt(pos + 5))) ||
                !"()Ljava/lang/String;".equals(constPool.getMethodrefType(codeIterator.u16bitAt(pos + 8))) ||
                !"getHost".equals(constPool.getMethodrefName(codeIterator.u16bitAt(pos + 8))) ||
                !getDocumentBaseType.equals(constPool.getMethodrefType(codeIterator.u16bitAt(pos + 20))) ||
                !"getDocumentBase".equals(constPool.getMethodrefName(codeIterator.u16bitAt(pos + 20))) ||
                !"()I".equals(constPool.getMethodrefType(codeIterator.u16bitAt(pos + 23))) ||
                !"getPort".equals(constPool.getMethodrefName(codeIterator.u16bitAt(pos + 23))))
            return;

        // Erase the port
        for (int i = 0; i < 15; i++) {
            codeIterator.writeByte(Opcode.NOP, pos + 13 + i);
        }

        if (LegacyFixAgent.isDebug()) {
            LFLogger.info("classicpatch", "Erased minecraftUri port");
        }
    }
}
