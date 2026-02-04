package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;

public class IntelGraphicsPatch extends Patch {
    public IntelGraphicsPatch() {
        super("intel", "Fixes rendering issues on older Intel GPUs", true);
    }

    @Override
    public void apply(final PatchPool patchPool) throws Exception {
        patchPool.addTransformer(new Transformer() {
            private boolean applied = false;

            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (applied || name == null) {
                    return null;
                }

                CtClass clas = ctFromBytes(bytecode);
                if (clas == null || name.startsWith("org.lwjgl") || clas.getDeclaredConstructors().length > 1 || clas.isFrozen()) {
                    return null;
                }

                try {
                    boolean openGlHelperMatched = false;

                    for (CtMethod glActiveTextureMethod : clas.getDeclaredMethods()) {
                        if (!Modifier.isPublic(glActiveTextureMethod.getModifiers()) ||
                            !Modifier.isStatic(glActiveTextureMethod.getModifiers()) ||
                            !"(I)V".equals(glActiveTextureMethod.getSignature())
                        ) {
                            continue;
                        }

                        MethodInfo mi = glActiveTextureMethod.getMethodInfo();
                        CodeAttribute ca = mi.getCodeAttribute();
                        if (ca == null) {
                            continue;
                        }

                        ConstPool cp = mi.getConstPool();
                        CodeIterator it = ca.iterator();
                        boolean methodPatched = false;

                        while (it.hasNext()) {
                            int pos = it.next();
                            int opcode = it.byteAt(pos);

                            if (opcode != Opcode.INVOKESTATIC) {
                                continue;
                            }

                            int index = it.u16bitAt(pos + 1);
                            String className = cp.getMethodrefClassName(index);
                            String methodName = cp.getMethodrefName(index);
                            String signature = cp.getMethodrefType(index);

                            String targetClientMethod = null;
                            if ("org.lwjgl.opengl.ARBMultitexture".equals(className) &&
                                "glActiveTextureARB".equals(methodName) &&
                                "(I)V".equals(signature)
                            ) {
                                targetClientMethod = "glClientActiveTextureARB";
                            } else if (
                                "org.lwjgl.opengl.GL13".equals(className) &&
                                "glActiveTexture".equals(methodName) &&
                                "(I)V".equals(signature)
                            ) {
                                targetClientMethod = "glClientActiveTexture";
                            }

                            if (targetClientMethod != null) {
                                int classIndex = cp.getMethodrefClass(index);
                                int nameAndTypeIndex = cp.addNameAndTypeInfo(targetClientMethod, "(I)V");
                                int newMethodIndex = cp.addMethodrefInfo(classIndex, nameAndTypeIndex);

                                byte[] code = new byte[] {
                                    (byte) Opcode.DUP,
                                    (byte) Opcode.INVOKESTATIC,
                                    (byte) (newMethodIndex >>> 8),
                                    (byte) (newMethodIndex)
                                };

                                it.insert(pos, code);
                                methodPatched = true;
                                Logger.debug("intel", "Patched call to " + className + "." + methodName);
                            }
                        }

                        if (methodPatched) {
                            ca.computeMaxStack();
                            openGlHelperMatched = true;
                        }
                    }

                    if (openGlHelperMatched) {
                        Logger.debug("intel", "Patched OpenGlHelper: " + clas.getName());
                        applied = true;
                        return clas.toBytecode();
                    }

                } catch (Throwable t) {
                    Logger.error("intel", t);
                }

                return null;
            }
        });
    }
}
