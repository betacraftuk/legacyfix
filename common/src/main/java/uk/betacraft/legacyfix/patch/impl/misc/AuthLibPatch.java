package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class AuthLibPatch extends Patch {
    public AuthLibPatch() {
        super("authlib-patch", "Patches authlib 1.5-1.5.5 to make it load player textures", true, false);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        String yggSessionServiceClass = "com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService";

        final CtClass gameProfileClass =  patchPool.getRawClass("com.mojang.authlib.GameProfile");
        if (gameProfileClass == null) {
            throw new PatchException("GameProfile class not found");
        }

        patchPool.addCtTransformer(yggSessionServiceClass, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod getTexturesMethod;
                try {
                    getTexturesMethod = ctClass.getDeclaredMethod("getTextures", new CtClass[]{gameProfileClass, CT_BOOLEAN});
                } catch (NotFoundException e) {
                    Logger.error("AuthLibPatch", "getTextures method not found");
                    return;
                }

                getTexturesMethod.instrument(new ExprEditor() {
                    public void edit(MethodCall call) throws CannotCompileException {
                        if (call.getMethodName().equals("hasSignature") || call.getMethodName().equals("isSignatureValid")) {
                            call.replace("{ $_ = true; }");
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        if (!super.shouldApply(patchPool)) {
            return false;
        }

        CtClass sessionServiceClass = patchPool.getRawClass("com.mojang.authlib.minecraft.MinecraftSessionService");
        if (sessionServiceClass == null) {
            return false;
        }

        boolean methodHasBoolArg = false;
        try {
            for (CtMethod method : sessionServiceClass.getDeclaredMethods()) {
                if (method.getName().equals("fillProfileProperties") && method.getParameterTypes().length == 2 &&
                        method.getParameterTypes()[1].getName().equals("boolean")) {
                    methodHasBoolArg = true;
                }
            }
        } catch (Throwable t) {
            Logger.error(this, t);
        }

        return !methodHasBoolArg;
    }
}
