package uk.betacraft.legacyfix.patch.impl.thirdparty;

import javassist.CtClass;
import javassist.NotFoundException;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

public class CommonsLangVersionPatch extends Patch {
    public CommonsLangVersionPatch() {
        super("commons-lang-version", "Makes old commons-lang3 versions handle Java 9+", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addCtTransformer("org.apache.commons.lang3.SystemUtils", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                try {
                    ctClass.getDeclaredField("JAVA_SPECIFICATION_VERSION_AS_ENUM");
                    ctClass.getDeclaredMethod("isJavaVersionAtLeast")
                        .insertBefore("if (JAVA_SPECIFICATION_VERSION_AS_ENUM == null) { return true; }");
                } catch (NotFoundException ignored) {
                }
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9;
    }
}
