package uk.betacraft.legacyfix.patch.impl.java;

import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

public class SecurityManagerPatch extends Patch {
    public SecurityManagerPatch() {
        super("security-manager", "Ignores attempts to set a SecurityManager on Java 18+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (!mentions(bytecode, "setSecurityManager")) {
                    return null;
                }

                byte[] result = instrument(name, bytecode, new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if ("java.lang.System".equals(m.getClassName()) && "setSecurityManager".equals(m.getMethodName())) {
                            m.replace("try { $proceed($$); } catch (java.lang.UnsupportedOperationException e) { }");
                        }
                    }
                });

                if (result != null) {
                    Logger.debug("security-manager", "Made setting a SecurityManager optional in class " + name);
                }
                return result;
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 18;
    }
}
