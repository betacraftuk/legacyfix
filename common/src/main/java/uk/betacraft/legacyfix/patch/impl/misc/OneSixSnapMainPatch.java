package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class OneSixSnapMainPatch extends Patch {
    public OneSixSnapMainPatch() {
        super("one-six-main", "Patches main() of versions 13w16a-13w23a to make them not crash with unknown arguments", true, false);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addCtTransformer("net.minecraft.client.main.Main", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod mainMethod = ctClass.getDeclaredMethod("main");
                mainMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "gameArgsClass.getMethod(\"setArgsRaw\", new Class[]{String[].class}).invoke(null, new Object[]{$1});" +
                    "$1 = (String[]) gameArgsClass.getMethod(\"getOneSixArguments\", null).invoke(null, null);"
                );
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        if (!super.shouldApply(patchPool)) {
            return false;
        }

        CtClass mainClass = patchPool.getRawClass("net.minecraft.client.main.Main");
        if (mainClass == null) {
            return false;
        }

        try {
            mainClass.getDeclaredMethod("main");
        } catch (NotFoundException e) {
            return false;
        }

        return Agent.hasSetting("lf.onesix"); // this depends on ProxyPatch running beforehand, make sure that's always the case
    }
}
