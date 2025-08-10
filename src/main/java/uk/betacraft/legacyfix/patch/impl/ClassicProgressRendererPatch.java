package uk.betacraft.legacyfix.patch.impl;

import javassist.*;
import javassist.bytecode.*;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

/**
 * Improves performance in Classic c0.0.13a - c0.29_02 by introducing the patch from c0.30
 */
public class ClassicProgressRendererPatch extends Patch {
    public ClassicProgressRendererPatch() {
        super("classic-performance", "Improves performance in c0.0.13a - c0.29_02", true);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtMethod method = findProgressRendererPercentageMethod();
        if (method == null)
            throw new PatchException("No progressPercentage method found");

        CtClass progressRendererClass = method.getDeclaringClass();
        if (progressRendererClass.isFrozen())
            progressRendererClass.defrost();

        // check if this is c0.30, if so then no need to apply this patch
        if (!PatchHelper.findMinecraftClass(pool).getName().equals(progressRendererClass.getName())) {
            for (CtField lastTimeCandidateField : progressRendererClass.getDeclaredFields()) {
                if (lastTimeCandidateField.getType().getName().equals("long")) {
                    throw new PatchException("Detected c0.30, patch won't be applied");
                }
            }
        }

        // reference to Minecraft.running
        String minecraftRunningRef = getMinecraftRunningField(progressRendererClass);
        if (minecraftRunningRef == null)
            throw new PatchException("Reference to Minecraft.running could not be made");

        CtClass helperClass = pool.makeClass("legacyfix.helper.ProgressRendererHelper");
        CtField lastTimeField = CtField.make("public static long lastTime = System.currentTimeMillis();", helperClass);

        helperClass.addField(lastTimeField);

        // make helper class available
        helperClass.toClass(progressRendererClass.getClass().getClassLoader(), progressRendererClass.getClass().getProtectionDomain());
        Class.forName(helperClass.getName());

        // @formatter:off
        method.insertBefore(
                "long time;" +
                "if (" + minecraftRunningRef + ") {" +
                "    if (!((time = System.currentTimeMillis()) - legacyfix.helper.ProgressRendererHelper.lastTime < 0L || time - legacyfix.helper.ProgressRendererHelper.lastTime >= 20L)) {" +
                "        return;" +
                "    } else {" +
                "        legacyfix.helper.ProgressRendererHelper.lastTime = time;" +
                "    }" +
                "}"
        );
        // @formatter:on

        inst.redefineClasses(new ClassDefinition(Class.forName(progressRendererClass.getName()), progressRendererClass.toBytecode()));
    }

    private String getMinecraftRunningField(CtClass progressRendererClass) {
        try {
            CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);

            String result = "$0";
            if (!progressRendererClass.equals(minecraftClass)) {
                CtField progressRendererMinecraftField = null;
                for (CtField candidateField : progressRendererClass.getDeclaredFields()) {
                    if (candidateField.getType().getName().equals(minecraftClass.getName())) {
                        progressRendererMinecraftField = candidateField;
                        break;
                    }
                }

                if (progressRendererMinecraftField == null)
                    throw new PatchException("No ProgressRenderer.minecraft field found");

                result += "." + progressRendererMinecraftField.getName();
            }

            CtField minecraftRunningField = null;
            int count = 0;
            for (CtField candidateField : minecraftClass.getDeclaredFields()) {
                if (!Modifier.isVolatile(candidateField.getModifiers())) continue;

                if (!candidateField.getType().getName().equals("boolean")) continue;

                count++;
                if (count == 2) {
                    minecraftRunningField = candidateField;
                    break;
                }
            }

            if (minecraftRunningField == null)
                throw new PatchException("No Minecraft.running field found");

            return result + "." + minecraftRunningField.getName();
        } catch (Exception e) {
            LFLogger.error("classic-performance", e);
        }
        return null;
    }

    private CtMethod findProgressRendererPercentageMethod() {
        try {
            CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);

            if (minecraftClass.isFrozen()) {
                minecraftClass.defrost();
            }

            // Try finding ProgressRenderer first
            for (CtField candidateField : minecraftClass.getDeclaredFields()) {
                CtClass candidateClass = candidateField.getType();
                for (CtConstructor candidateConstructor : candidateClass.getDeclaredConstructors()) {
                    CtClass[] paramTypes = candidateConstructor.getParameterTypes();
                    if (paramTypes.length != 1)
                        continue;

                    if (!paramTypes[0].getName().equals(minecraftClass.getName()))
                        continue;

                    for (CtMethod candidateMethod : candidateClass.getDeclaredMethods()) {
                        paramTypes = candidateMethod.getParameterTypes();
                        if (paramTypes.length != 1)
                            continue;

                        if (!paramTypes[0].getName().equals("int"))
                            continue;

                        if (isProgressRendererPercentageMethod(candidateMethod)) {
                            LFLogger.debug("classic-performance",
                                "Found ProgressRenderer.progressPercentage method:",
                                candidateMethod.getDeclaringClass().getName(),
                                candidateMethod.getName()
                            );
                            return candidateMethod;
                        }
                    }
                }
            }

            // ProgressRenderer class not found, the method must be in Minecraft class
            for (CtMethod candidateMethod : minecraftClass.getDeclaredMethods()) {
                CtClass[] paramTypes = candidateMethod.getParameterTypes();
                if (paramTypes.length != 1)
                    continue;

                if (!paramTypes[0].getName().equals("int"))
                    continue;

                if (isProgressRendererPercentageMethod(candidateMethod)) {
                    LFLogger.debug("classic-performance",
                        "Found Minecraft.progressPercentage method:",
                        candidateMethod.getDeclaringClass().getName(),
                        candidateMethod.getName()
                    );
                    return candidateMethod;
                }
            }

            return null;
        } catch (Throwable t) {
            LFLogger.error("classic-performance", t);
            return null;
        }
    }

    private static boolean isProgressRendererPercentageMethod(CtMethod method) {
        try {
            CodeAttribute codeAttribute = method.getMethodInfo().getCodeAttribute();
            CodeIterator codeIterator = codeAttribute.iterator();
            ConstPool cp = method.getMethodInfo().getConstPool();

            while (codeIterator.hasNext()) {
                int pos = codeIterator.next();
                int opcode = codeIterator.byteAt(pos);

                String dirtPngCandidate;
                if (opcode == Opcode.LDC) {
                    if (!PatchHelper.isString(cp, codeIterator.byteAt(pos + 1))) continue;
                    dirtPngCandidate = cp.getStringInfo(codeIterator.byteAt(pos + 1));
                } else if (opcode == Opcode.LDC_W) {
                    if (!PatchHelper.isUtf8(cp, codeIterator.u16bitAt(pos + 1))) continue;
                    dirtPngCandidate = cp.getStringInfo(codeIterator.u16bitAt(pos + 1));
                } else continue;

                if ("/dirt.png".equals(dirtPngCandidate)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    @Override
    public boolean shouldApply() {
        return super.shouldApply() && pool.getOrNull("com.mojang.minecraft.MinecraftApplet") != null;
    }
}
