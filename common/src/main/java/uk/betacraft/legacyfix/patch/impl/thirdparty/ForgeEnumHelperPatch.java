package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

public class ForgeEnumHelperPatch extends Patch {
    private static final String[] CLASSES = {
        "forge.EnumHelper",
        "net.minecraft.src.forge.EnumHelper",
        "net.minecraftforge.common.EnumHelper"
    };
    private static final String HELPER = "uk.betacraft.legacyfix.jvm.UnsafeReflection";
    private static final String SETUP_SIMPLE = "{ isSetup = true; }";
    private static final String SETUP_DECOMPILED = "" +
        "{" +
        "    if (isSetup) {" +
        "        return;" +
        "    }" +
        "    for (int x = 0; x < ctrs.length; x++) {" +
        "        try {" +
        "            java.lang.Class[] enumHeaders = new java.lang.Class[ctrs[x].length + 3];" +
        "            enumHeaders[0] = java.lang.String.class;" +
        "            enumHeaders[1] = java.lang.Integer.TYPE;" +
        "            enumHeaders[2] = java.lang.String.class;" +
        "            enumHeaders[3] = java.lang.Integer.TYPE;" +
        "            for (int y = 1; y < ctrs[x].length; y++) {" +
        "                enumHeaders[3 + y] = ctrs[x][y];" +
        "            }" +
        "            ctrs[x][0].getDeclaredConstructor(enumHeaders);" +
        "            decompiledFlags[x] = true;" +
        "        } catch (java.lang.Exception e) {" +
        "        }" +
        "    }" +
        "    isSetup = true;" +
        "}";

    public ForgeEnumHelperPatch() {
        super("forge-enumhelper", "Makes the old Forge's EnumHelper work on Java 9+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        CtTransformer transformer = new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                boolean detectsDecompiled = true;
                try {
                    ctClass.getDeclaredField("decompiledFlags");
                } catch (NotFoundException e) {
                    detectsDecompiled = false;
                }
                ctClass.getDeclaredMethod("setup").setBody(detectsDecompiled ? SETUP_DECOMPILED : SETUP_SIMPLE);

                ctClass.getDeclaredMethod("getConstructorAccessor").instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (isInvoke(m)) {
                            m.replace("$_ = $2[0];");
                        }
                    }
                });

                ctClass.getDeclaredMethod("makeEnum").instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (isInvoke(m)) {
                            m.replace("$_ = " + invokeStatic(HELPER, "newInstance",
                                new String[] {"java.lang.reflect.Constructor.class", "java.lang.Object[].class"},
                                new String[] {"(java.lang.reflect.Constructor) $1", "(java.lang.Object[]) $2[0]"}
                            ) + ";");
                        }
                    }
                });

                CtMethod setFailsafe = ctClass.getDeclaredMethod("setFailsafeFieldValue");
                setFailsafe.setBody("{" + invokeStatic(HELPER, "setFieldValue",
                    new String[] {"java.lang.reflect.Field.class", "java.lang.Object.class", "java.lang.Object.class"},
                    new String[] {"$1", "$2", "$3"}
                ) + ";}");
            }
        };

        for (String className : CLASSES) {
            patchPool.addCtTransformer(className, transformer);
        }
    }

    private static boolean isInvoke(MethodCall m) {
        return "java.lang.reflect.Method".equals(m.getClassName()) && "invoke".equals(m.getMethodName());
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9;
    }
}
