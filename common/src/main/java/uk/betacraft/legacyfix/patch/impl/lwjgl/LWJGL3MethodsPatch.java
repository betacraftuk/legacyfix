package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.*;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;

public class LWJGL3MethodsPatch extends Patch {
    public LWJGL3MethodsPatch() {
        super("lwjgl3-patch", "Changes references to LWJGL 3.1.2's MemoryUtil and GLFW to be compliant with LWJGL 3.1.3+", false);
    }

    @Override
    public void apply(final PatchPool patchPool) throws Exception {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (name == null) {
                    return null;
                }

                CtClass clas = ctFromBytes(bytecode);
                if (clas == null || name.startsWith("org.lwjgl") || clas.isFrozen()) {
                    return null;
                }

                try {
                    boolean classPatched = false;

                    for (CtConstructor constructor : clas.getDeclaredConstructors()) {
                        MethodInfo mi = constructor.getMethodInfo();

                        if (patchRefs(mi)) {
                            classPatched = true;
                        }
                    }

                    for (CtMethod method : clas.getDeclaredMethods()) {
                        MethodInfo mi = method.getMethodInfo();

                        if (patchRefs(mi)) {
                            classPatched = true;
                        }
                    }

                    if (classPatched) {
                        Logger.debug("lwjgl3-patch", "Patched LWJGL 3.1.2 references in: " + clas.getName());
                        return clas.toBytecode();
                    }

                } catch (Throwable t) {
                    Logger.error("lwjgl3-patch", t);
                }

                return null;
            }
        });
    }

    private boolean patchRefs(MethodInfo mi) throws BadBytecode {
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) {
            return false;
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

            String targetMethod = null;
            String targetMethodSig = null;
            byte[] insert = null;
            if ("org.lwjgl.system.MemoryUtil".equals(className) &&
                "memCopy".equals(methodName) &&
                "(JJI)V".equals(signature)
            ) {
                targetMethod = "memCopy";
                targetMethodSig = "(JJJ)V";
                insert = new byte[] {
                    (byte) Opcode.I2L
                };
            } else if (
                "org.lwjgl.glfw.GLFW".equals(className) &&
                    "glfwGetError".equals(methodName) &&
                    "()I".equals(signature)
            ) {
                targetMethod = "glfwGetError";
                targetMethodSig = "(Lorg/lwjgl/PointerBuffer;)I";
                insert = new byte[] {
                    (byte) Opcode.ACONST_NULL
                };
            }

            if (targetMethod != null) {
                int classIndex = cp.getMethodrefClass(index);
                int nameAndTypeIndex = cp.addNameAndTypeInfo(targetMethod, targetMethodSig);
                int newMethodIndex = cp.addMethodrefInfo(classIndex, nameAndTypeIndex);

                byte[] code = new byte[] {
                    (byte) Opcode.INVOKESTATIC,
                    (byte) (newMethodIndex >>> 8),
                    (byte) (newMethodIndex)
                };

                it.write(code, pos);
                it.insert(pos, insert);
                methodPatched = true;
                Logger.debug("lwjgl3-patch", "Patched call to " + className + "." + methodName);
            }
        }

        if (methodPatched) {
            ca.computeMaxStack();
        }
        return methodPatched;
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        if (!super.shouldApply(patchPool)) {
            return false;
        }

        CtClass versionClass = patchPool.getRawClass("org.lwjgl.Version");
        if (versionClass == null) {
            return false;
        }

        int majorVersion;
        int minorVersion;
        int revisionVersion;
        try {
            majorVersion = (Integer) versionClass.getDeclaredField("VERSION_MAJOR").getConstantValue();
            minorVersion = (Integer) versionClass.getDeclaredField("VERSION_MINOR").getConstantValue();
            revisionVersion = (Integer) versionClass.getDeclaredField("VERSION_REVISION").getConstantValue();
        } catch (NotFoundException ignored) {
            return false;
        } catch (ClassCastException ignored) {
            return false;
        }

        return majorVersion == 3 && minorVersion >= 1 && !(minorVersion == 1 && revisionVersion < 3);
    }
}
