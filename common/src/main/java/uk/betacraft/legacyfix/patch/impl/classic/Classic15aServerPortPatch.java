package uk.betacraft.legacyfix.patch.impl.classic;

import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.Opcode;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class Classic15aServerPortPatch extends Patch {
    public Classic15aServerPortPatch() {
        super("c0.0.15a-server-port", "Patches Classic 0.0.15a's Minecraft.setServer method to not hardcode the server port", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        String minecraftClassName = GameClasses.findMinecraftClass(patchPool);
        if (minecraftClassName == null) {
            throw new PatchException("No Minecraft class name found");
        }

        final String setServerMethodName = GameClasses.findSetServerMethodName(patchPool);
        if (setServerMethodName == null) {
            throw new PatchException("No setServer method name found");
        }

        patchPool.addCtTransformer(minecraftClassName, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod setServerMethod = ctClass.getDeclaredMethod(setServerMethodName, new CtClass[]{CT_STRING, CT_INT});
                try {
                    CodeAttribute codeAttribute = setServerMethod.getMethodInfo().getCodeAttribute();
                    if (codeAttribute == null) {
                        return;
                    }

                    CodeIterator codeIterator = codeAttribute.iterator();

                    while (codeIterator.hasNext()) {
                        int pos = codeIterator.next();
                        int opcode = codeIterator.byteAt(pos);

                        if (opcode != Opcode.SIPUSH) {
                            continue;
                        }

                        int provided = codeIterator.s16bitAt(pos + 1);
                        if (provided != 5565) {
                            Logger.debug("c0.0.15a-server-port",
                                "SIPUSH does not provide 5565, instead " + provided + ". The client is very likely modified.",
                                "The patch did not apply."
                            );
                            continue;
                        }

                        codeIterator.writeByte(Opcode.ILOAD_2, pos);
                        codeIterator.writeByte(Opcode.NOP, pos + 1);
                        codeIterator.writeByte(Opcode.NOP, pos + 2);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && Agent.getSetting("lf.version", "").equals("c0.0.15a");
    }
}
