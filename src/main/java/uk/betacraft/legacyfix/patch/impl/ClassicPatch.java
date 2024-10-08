package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixLauncher;
import uk.betacraft.legacyfix.patch.Patch;
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
        if (minecraftAppletClass.isFrozen())
            minecraftAppletClass.defrost();

        CtMethod initMethod = minecraftAppletClass.getDeclaredMethod("init");

        ConstPool initConstPool = initMethod.getMethodInfo().getConstPool();

        CodeAttribute codeAttribute = initMethod.getMethodInfo().getCodeAttribute();
        CodeIterator codeIterator = codeAttribute.iterator();

        while (codeIterator.hasNext()) {
            int pos = codeIterator.next();

            patch15aServerJoin(codeIterator, initConstPool, pos);
            patchMinecraftUriPort(codeIterator, initConstPool, pos);
        }

        inst.redefineClasses(new ClassDefinition(Class.forName(minecraftAppletClass.getName()), minecraftAppletClass.toBytecode()));
    }

    public static void patch15aServerJoin(CodeIterator codeIterator, ConstPool constPool, int pos) {
        String server = System.getProperty("server", null);
        int port = Integer.parseInt(System.getProperty("port", "25565"));

        if (codeIterator.byteAt(pos) != Opcode.ALOAD_0 ||
                codeIterator.byteAt(pos + 1) != Opcode.GETFIELD ||
                codeIterator.byteAt(pos + 4) != Opcode.LDC ||
                codeIterator.byteAt(pos + 6) != Opcode.SIPUSH ||
                codeIterator.byteAt(pos + 9) != Opcode.INVOKEVIRTUAL) {
            return;
        }

        int ldcPos = codeIterator.byteAt(pos + 5);
        if (constPool.getTag(ldcPos) != ConstPool.CONST_String)
            return;

        String defaultString = constPool.getStringInfo(ldcPos);
        int defaultPort = codeIterator.s16bitAt(pos + 7);

        if (!"79.136.77.240".equals(defaultString))
            return;

        if (defaultPort != 5565)
            return;

        String refType = constPool.getMethodrefType(codeIterator.u16bitAt(pos + 10));
        if (!"(Ljava/lang/String;I)V".equals(refType))
            return;

        if (server != null) {
            // Inject the intended server
            int serverRef = constPool.addStringInfo(server);

            codeIterator.writeByte(serverRef, pos + 5);
            codeIterator.write16bit(port, pos + 7);
        } else {
            // Erase the call
            for (int i = 0; i < 12; i++) {
                codeIterator.writeByte(Opcode.NOP, pos + i);
            }
        }
    }

    /**
     * Patch for negative port number during saving
     */
    public static void patchMinecraftUriPort(CodeIterator codeIterator, ConstPool constPool, int pos) {
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
        if (constPool.getTag(ldcPos) != ConstPool.CONST_String)
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
    }
}
