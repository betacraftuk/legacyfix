package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class BetaForgePatch extends Patch {
    public BetaForgePatch() {
        super("beta-forge", "Fixes Forge on beta versions", false);
    }

    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addCtTransformer("forge.ForgeHooksClient", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                ctClass.instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("toArray") && m.getSignature().equals("()[Ljava/lang/Object;")) {
                            m.replace("$_ = $0.toArray(new Integer[0]);");
                        }
                    }
                });
            }
        });
    }
}
