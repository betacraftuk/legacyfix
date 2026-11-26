package uk.betacraft.legacyfix.patch.impl.java;

import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

public class AnonymousClassPatch extends Patch {
    public AnonymousClassPatch() {
        super("anonymous-class", "Reimplements Unsafe#defineAnonymousClass on Java 17+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (!mentions(bytecode, "defineAnonymousClass")) {
                    return null;
                }

                byte[] result = instrument(name, bytecode, new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if ("sun.misc.Unsafe".equals(m.getClassName()) && "defineAnonymousClass".equals(m.getMethodName())) {
                            m.replace(
                                "$_ = (java.lang.Class) " + invokeStatic(
                                    "uk.betacraft.legacyfix.jvm.AnonymousClassStub",
                                    "define",
                                    new String[] {"java.lang.Object.class", "java.lang.Object.class", "java.lang.Object.class"},
                                    new String[] {"$1", "$2", "$3"}
                                ) + ";"
                            );
                        }
                    }
                });

                if (result != null) {
                    Logger.debug("anonymous-class", "Redirected defineAnonymousClass in " + name);
                }
                return result;
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 17;
    }
}
