package uk.betacraft.legacyfix.patch.api;

import javassist.CtClass;

public interface CtTransformer {
    void transform(CtClass ctClass) throws Exception;
}
