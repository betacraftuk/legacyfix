package uk.betacraft.legacyfix.patch.impl.java;

import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

/**
 * ShockAhPI finds the game instance by reading the {@code Runnable} of "Minecraft main thread"
 * through Thread#target (???), which no longer exists since Java 19.
 */
public class ThreadTargetPatch extends Patch {
    private static final String STUB = "uk.betacraft.legacyfix.jvm.ThreadTargetStub";
    private static final String FIELD = "java.lang.reflect.Field";

    public ThreadTargetPatch() {
        super("thread-target", "Makes reflective access to Thread#target work on Java 19+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (!mentions(bytecode, "java/lang/reflect/Field") || !mentions(bytecode, "java/lang/Thread")
                    || !mentions(bytecode, "target")) {
                    return null;
                }

                byte[] result = instrument(name, bytecode, new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        String cls = m.getClassName();
                        String method = m.getMethodName();

                        if ("java.lang.Class".equals(cls) && "getDeclaredField".equals(method)) {
                            m.replace("" +
                                "if ($0 == java.lang.Thread.class && \"target\".equals($1)) {" +
                                "    $_ = (" + FIELD + ") " + invokeStatic(STUB, "field", new String[0], new String[0]) + ";" +
                                "} else {" +
                                "    $_ = $proceed($$);" +
                                "}"
                            );
                            return;
                        }

                        if (FIELD.equals(cls) && "get".equals(method)) {
                            m.replace("" +
                                "if (\"target\".equals($0.getName()) && ((java.lang.Boolean) " +
                                    invokeStatic(STUB, "isTargetField", new String[] {FIELD + ".class"}, new String[] {"$0"}) + ").booleanValue()) {" +
                                "    $_ = " + invokeStatic(STUB, "read", new String[] {"java.lang.Object.class"}, new String[] {"$1"}) + ";" +
                                "} else {" +
                                "    $_ = $proceed($$);" +
                                "}"
                            );
                        }
                    }
                });

                if (result != null) {
                    Logger.debug("thread-target", "Redirected Thread#target access in class " + name);
                }
                return result;
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 19;
    }
}
