package uk.betacraft.legacyfix.patch.api;

import javassist.CtClass;

public interface PatchPool {
    CtClass getClass(String className);

    void patchClass(CtClass patchedClass);

    void addTransformer(Transformer transformer);
}
