package uk.betacraft.legacyfix.patch.impl.java;

import uk.betacraft.legacyfix.patch.api.ClassRedirectPatch;

public class SunReflectPatch extends ClassRedirectPatch {
    private static final String STAND_IN = "uk/betacraft/legacyfix/jvm/sunreflect/";

    public SunReflectPatch() {
        super("sun-reflect", "Provides Java 8's sun.reflect field and constructor accessors", 9,
            "sun/reflect/ReflectionFactory", STAND_IN + "ReflectionFactory",
            "sun/reflect/FieldAccessor", STAND_IN + "FieldAccessor",
            "sun/reflect/ConstructorAccessor", STAND_IN + "ConstructorAccessor");
    }
}
