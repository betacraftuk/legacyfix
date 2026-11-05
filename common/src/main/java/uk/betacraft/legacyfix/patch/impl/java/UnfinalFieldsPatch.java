package uk.betacraft.legacyfix.patch.impl.java;

import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

public class UnfinalFieldsPatch extends Patch {
    private static final String REMOVER = "uk.betacraft.legacyfix.jvm.FinalFieldRemover";
    private static final String STUB = "uk.betacraft.legacyfix.jvm.ModifiersStub";
    private static final String FIELD = "java.lang.reflect.Field";

    private static final String[] LEGACY_MOD_LOADERS = {
        "ModLoader", "net.minecraft.src.ModLoader",
        "cpw.mods.fml.common.Loader", "net.minecraftforge.fml.common.Loader",
        "net.minecraft.src.forge.MinecraftForge", "net.minecraftforge.common.MinecraftForge"
    };

    public UnfinalFieldsPatch() {
        super("unfinal-fields", "Makes reflective removal of the final modifier work on Java 9+", false);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (!mentions(bytecode, "java/lang/reflect/Field") || !mentions(bytecode, "modifiers")) {
                    return null;
                }

                byte[] result = instrument(name, bytecode, new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        String cls = m.getClassName();
                        String method = m.getMethodName();

                        if ("java.lang.Class".equals(cls) && "getDeclaredField".equals(method)) {
                            m.replace("" +
                                "if ($0 == " + FIELD + ".class && \"modifiers\".equals($1)) {" +
                                "    $_ = (" + FIELD + ") " + invokeStatic(STUB, "field", new String[0], new String[0]) + ";" +
                                "} else {" +
                                "    $_ = $proceed($$);" +
                                "}"
                            );
                            return;
                        }

                        if (!FIELD.equals(cls)) {
                            return;
                        }

                        String condition = valueCondition(method);
                        if (condition != null) {
                            m.replace("" +
                                "if (" + isModifiersField() + " && $1 instanceof " + FIELD + condition + ") {" +
                                "    " + invokeStatic(REMOVER, "removeFinal", new String[] {FIELD + ".class"}, new String[] {"$1"}) + ";" +
                                "} else if (" + isUnfinalStatic() + ") {" +
                                "    " + invokeStatic(REMOVER, "setStatic", new String[] {FIELD + ".class", "java.lang.Object.class"}, new String[] {"$0", "($w) $2"}) + ";" +
                                "} else {" +
                                "    $proceed($$);" +
                                "}"
                            );
                            return;
                        }

                        String read = readExpression(method);
                        if (read != null) {
                            m.replace("" +
                                "if (" + isModifiersField() + " && $1 instanceof " + FIELD + ") {" +
                                "    $_ = " + read + ";" +
                                "} else {" +
                                "    $_ = $proceed($$);" +
                                "}"
                            );
                        }
                    }
                });

                if (result != null) {
                    Logger.debug("unfinal-fields", "Redirected Field modifiers access in class " + name);
                }
                return result;
            }
        });
    }

    private static String valueCondition(String method) {
        String dropsFinal = " && ((%s & " + java.lang.reflect.Modifier.FINAL + ") == 0)";

        if ("set".equals(method)) {
            return " && $2 instanceof java.lang.Integer" + String.format(dropsFinal, "((java.lang.Integer) $2).intValue()");
        }
        if ("setInt".equals(method) || "setShort".equals(method) || "setByte".equals(method) || "setChar".equals(method)) {
            return String.format(dropsFinal, "(int) $2");
        }
        if ("setLong".equals(method) || "setFloat".equals(method) || "setDouble".equals(method) || "setBoolean".equals(method)) {
            return "";
        }
        return null;
    }

    private static String isModifiersField() {
        return "((java.lang.Boolean) " + invokeStatic(STUB, "isModifiersField", new String[] {FIELD + ".class"}, new String[] {"$0"}) + ").booleanValue()";
    }

    private static String isUnfinalStatic() {
        return "((java.lang.Boolean) " + invokeStatic(REMOVER, "isUnfinalStatic", new String[] {FIELD + ".class"}, new String[] {"$0"}) + ").booleanValue()";
    }

    private static String readExpression(String method) {
        String value = "((java.lang.Integer) " + invokeStatic(STUB, "read", new String[] {FIELD + ".class"}, new String[] {"$1"}) + ").intValue()";

        if ("get".equals(method)) {
            return "java.lang.Integer.valueOf(" + value + ")";
        }
        if ("getInt".equals(method)) {
            return value;
        }
        if ("getLong".equals(method)) {
            return "(long) " + value;
        }
        if ("getFloat".equals(method)) {
            return "(float) " + value;
        }
        if ("getDouble".equals(method)) {
            return "(double) " + value;
        }
        return null;
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        if (JvmUtils.getJvmVersion() < 9) {
            return false;
        }

        if (super.shouldApply(patchPool)) {
            return true;
        }

        return !Agent.hasSetting("lf." + getId() + ".disable") && hasLegacyModLoader(patchPool);
    }

    private static boolean hasLegacyModLoader(PatchPool patchPool) {
        for (String loader : LEGACY_MOD_LOADERS) {
            if (patchPool.getRawClass(loader) != null) {
                return true;
            }
        }
        return false;
    }
}
