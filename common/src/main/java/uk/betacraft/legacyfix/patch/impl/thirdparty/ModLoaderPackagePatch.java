package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

public class ModLoaderPackagePatch extends Patch {
    public ModLoaderPackagePatch() {
        super("modloader-package", "Fixes RML not finding mods on Java 9+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addCtTransformer("ModLoader", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                ctClass.instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if ("java.lang.Class".equals(m.getClassName()) && "getPackage".equals(m.getMethodName())) {
                            m.replace("" +
                                "$_ = $proceed($$);" +
                                "if ($_ != null && $_.getName().length() == 0) {" +
                                "    $_ = null;" +
                                "}"
                            );
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9;
    }
}
