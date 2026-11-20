package uk.betacraft.legacyfix.patch.impl.java;

import uk.betacraft.legacyfix.patch.api.ClassRedirectPatch;

public class Pack200Patch extends ClassRedirectPatch {
    public Pack200Patch() {
        super("pack200", "Provides Pack200 unpacking, removed in Java 14", 14,
            "java/util/jar/Pack200", "uk/betacraft/legacyfix/jvm/Pack200");
    }
}
