package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

public class ModLoaderPackagePatch extends Patch {
    public ModLoaderPackagePatch() {
        super("modloader-package", "Fixes default package lookups on Java 9+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (!mentions(bytecode, "getPackage")) {
                    return null;
                }

                byte[] result = instrument(name, bytecode, new ExprEditor() {
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

                if (result != null) {
                    Logger.debug("modloader-package", "Redirected getPackage in " + name);
                }
                return result;
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9;
    }
}
