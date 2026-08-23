package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class DemoPatch extends Patch {
    public DemoPatch() {
        super("demo-patch", "Fixes Prism's demo argument override", true, false);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        String appletClass = GameClasses.findMinecraftAppletClass(patchPool);
        if (appletClass == null) {
            throw new PatchException("Minecraft applet class not found");
        }

        patchPool.addCtTransformer(appletClass, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod initMethod = ctClass.getDeclaredMethod("init");
                if (initMethod == null) {
                    return;
                }

                initMethod.instrument(new ExprEditor() {
                    public void edit(MethodCall call) throws CannotCompileException {
                        if (call.getMethodName().equals("getParameter")) {
                            call.replace("{ $_ = $proceed($$); if (\"demo\".equals($1) && \"false\".equals($_)) $_ = null; }");
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && patchPool.getRawClass("net.minecraft.client.MinecraftApplet") != null;
    }
}
