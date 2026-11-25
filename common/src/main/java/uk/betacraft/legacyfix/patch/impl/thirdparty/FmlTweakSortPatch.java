package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

public class FmlTweakSortPatch extends Patch {
    public FmlTweakSortPatch() {
        super("fml-tweak-sort", "Fixes old FML tweak sorting on Java 8+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addCtTransformer("cpw.mods.fml.relauncher.CoreModManager", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod sortTweakList;
                try {
                    sortTweakList = ctClass.getDeclaredMethod("sortTweakList");
                } catch (NotFoundException e) {
                    return;
                }

                sortTweakList.instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (!"java.util.Collections".equals(m.getClassName()) || !"sort".equals(m.getMethodName())) {
                            return;
                        }

                        m.replace("" +
                            "{" +
                            "   Object[] sorted = $1.toArray();" +
                            "   java.util.Arrays.sort(sorted, $2);" +
                            "   for (int i = 0; i < sorted.length; i++) {" +
                            "       $1.set(i, sorted[i]);" +
                            "   }" +
                            "}"
                        );
                    }
                });
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 8;
    }
}
