package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

/**
 * Forge parses the Java version as java.version.split("\\.")[1]
 */
public class ForgeJavaVersionPatch extends Patch {
    private static final String LOADER = "net.minecraftforge.fml.common.Loader";

    public ForgeJavaVersionPatch() {
        super("forge-java-version", "Makes Forge's Java 8 check recognize Java 9+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addCtTransformer(LOADER, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
                    constructor.instrument(new ExprEditor() {
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (!"java.lang.System".equals(m.getClassName()) || !"getProperty".equals(m.getMethodName())) {
                                return;
                            }

                            m.replace("" +
                                "$_ = $proceed($$);" +
                                "if (\"java.version\".equals($1) && $_ != null && !$_.startsWith(\"1.\")) {" +
                                "    $_ = \"1.\" + $_;" +
                                "}"
                            );
                        }
                    });
                }
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9;
    }
}
