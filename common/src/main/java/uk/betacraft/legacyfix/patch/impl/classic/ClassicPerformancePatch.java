package uk.betacraft.legacyfix.patch.impl.classic;

import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.PatchUnapplicableException;

public class ClassicPerformancePatch extends Patch {
    private PatchPool patchPool;

    public ClassicPerformancePatch() {
        super("classic-performance", "Improves performance in c0.0.13a - c0.29_02", true, false);
    }

    private static boolean isProgressRendererPercentageMethod(CtMethod method) {
        try {
            CodeAttribute codeAttribute = method.getMethodInfo().getCodeAttribute();
            if (codeAttribute == null) {
                return false;
            }

            CodeIterator codeIterator = codeAttribute.iterator();
            ConstPool cp = method.getMethodInfo().getConstPool();

            while (codeIterator.hasNext()) {
                int pos = codeIterator.next();
                int opcode = codeIterator.byteAt(pos);

                String dirtPngCandidate;
                if (opcode == Opcode.LDC) {
                    if (!isString(cp, codeIterator.byteAt(pos + 1))) {
                        continue;
                    }
                    dirtPngCandidate = cp.getStringInfo(codeIterator.byteAt(pos + 1));
                } else if (opcode == Opcode.LDC_W) {
                    if (!isString(cp, codeIterator.u16bitAt(pos + 1))) {
                        continue;
                    }
                    dirtPngCandidate = cp.getStringInfo(codeIterator.u16bitAt(pos + 1));
                } else {
                    continue;
                }

                if ("/dirt.png".equals(dirtPngCandidate)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        this.patchPool = patchPool;

        final CtMethod method = findProgressRendererPercentageMethod();
        if (method == null) {
            throw new PatchException("No progressPercentage method found");
        }

        CtClass progressRendererClass = method.getDeclaringClass();
        final String methodName = method.getName();

        if (!GameClasses.findMinecraftClass(patchPool).equals(progressRendererClass.getName())) {
            for (CtField field : progressRendererClass.getDeclaredFields()) {
                if ("long".equals(field.getType().getName())) {
                    throw new PatchUnapplicableException("Detected c0.30");
                }
            }
        }

        final String runningField = findRunningField(progressRendererClass);
        if (runningField == null) {
            throw new PatchException("No Minecraft.running field found");
        }

        patchPool.addCtTransformer(progressRendererClass.getName(), new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                ClassPool classPool = ctClass.getClassPool();
                CtClass helperClass = classPool.getOrNull("uk.betacraft.legacyfix.runtime.ProgressRenderer");
                if (helperClass == null) {
                    helperClass = classPool.makeClass("uk.betacraft.legacyfix.runtime.ProgressRenderer");
                    helperClass.addField(CtField.make("public static long lastTime = System.currentTimeMillis();", helperClass));
                }

                try {
                    helperClass.toClass();
                } catch (LinkageError error) {
                    Logger.error("Failed to link ProgressRenderer helper", error);
                }
                Class.forName(helperClass.getName(), true, classPool.getClassLoader());

                CtMethod progressMethod = ctClass.getDeclaredMethod(methodName, new CtClass[]{CtClass.intType});
                progressMethod.insertBefore("" +
                    "long time;" +
                    "if (" + runningField + ") {" +
                    "    if (!((time = System.currentTimeMillis()) - uk.betacraft.legacyfix.runtime.ProgressRenderer.lastTime < 0L || time - uk.betacraft.legacyfix.runtime.ProgressRenderer.lastTime >= 20L)) {" +
                    "        return;" +
                    "    } else {" +
                    "        uk.betacraft.legacyfix.runtime.ProgressRenderer.lastTime = time;" +
                    "    }" +
                    "}"
                );
            }
        });
    }

    private String findRunningField(CtClass progressRendererClass) {
        try {
            CtClass minecraftClass = patchPool.getRawClass(GameClasses.findMinecraftClass(patchPool));

            String result = "$0";
            if (!progressRendererClass.equals(minecraftClass)) {
                CtField progressRendererMinecraftField = null;
                for (CtField candidateField : progressRendererClass.getDeclaredFields()) {
                    if (candidateField.getType().getName().equals(minecraftClass.getName())) {
                        progressRendererMinecraftField = candidateField;
                        break;
                    }
                }

                if (progressRendererMinecraftField == null) {
                    throw new PatchException("No ProgressRenderer.minecraft field found");
                }

                result += "." + progressRendererMinecraftField.getName();
            }

            CtField minecraftRunningField = null;
            int count = 0;
            for (CtField candidateField : minecraftClass.getDeclaredFields()) {
                if (!Modifier.isVolatile(candidateField.getModifiers())) {
                    continue;
                }

                if (!candidateField.getType().getName().equals("boolean")) {
                    continue;
                }

                count++;
                if (count == 2) {
                    minecraftRunningField = candidateField;
                    break;
                }
            }

            if (minecraftRunningField == null) {
                return null;
            }

            return result + "." + minecraftRunningField.getName();
        } catch (Exception e) {
            Logger.error("classic-performance", e);
        }
        return null;
    }

    private CtMethod findProgressRendererPercentageMethod() {
        try {
            CtClass minecraftClass = patchPool.getRawClass(GameClasses.findMinecraftClass(patchPool));

            for (CtField candidateField : minecraftClass.getDeclaredFields()) {
                CtClass candidateClass = candidateField.getType();
                for (CtConstructor candidateConstructor : candidateClass.getDeclaredConstructors()) {
                    CtClass[] paramTypes = candidateConstructor.getParameterTypes();
                    if (paramTypes.length != 1) {
                        continue;
                    }

                    if (!paramTypes[0].getName().equals(minecraftClass.getName())) {
                        continue;
                    }

                    for (CtMethod candidateMethod : candidateClass.getDeclaredMethods()) {
                        paramTypes = candidateMethod.getParameterTypes();
                        if (paramTypes.length != 1) {
                            continue;
                        }

                        if (!paramTypes[0].getName().equals("int")) {
                            continue;
                        }

                        if (isProgressRendererPercentageMethod(candidateMethod)) {
                            Logger.debug("classic-performance",
                                "Found ProgressRenderer.progressPercentage method:",
                                candidateMethod.getDeclaringClass().getName(),
                                candidateMethod.getName()
                            );
                            return candidateMethod;
                        }
                    }
                }
            }

            for (CtMethod candidateMethod : minecraftClass.getDeclaredMethods()) {
                CtClass[] paramTypes = candidateMethod.getParameterTypes();
                if (paramTypes.length != 1) {
                    continue;
                }

                if (!"int".equals(paramTypes[0].getName())) {
                    continue;
                }

                if (isProgressRendererPercentageMethod(candidateMethod)) {
                    Logger.debug("classic-performance",
                        "Found Minecraft.progressPercentage method:",
                        candidateMethod.getDeclaringClass().getName(),
                        candidateMethod.getName()
                    );
                    return candidateMethod;
                }
            }

            return null;
        } catch (Throwable t) {
            Logger.error("classic-performance", t);
            return null;
        }
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && patchPool.getRawClass("com.mojang.minecraft.MinecraftApplet") != null;
    }
}
