package uk.betacraft.legacyfix.patch.api;

import javassist.CtClass;

public interface PatchPool {
    CtClass getRawClass(String className);

    void addTransformer(Transformer transformer);

    void addCtTransformer(String className, CtTransformer transformer);
}
