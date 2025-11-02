package uk.betacraft.legacyfix.patch;

import javassist.CtClass;

public interface PatchTransformer {
    CtClass getClass(String className);

    void patchClass(CtClass patchedClass);
}
