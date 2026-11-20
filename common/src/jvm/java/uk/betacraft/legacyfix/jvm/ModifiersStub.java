package uk.betacraft.legacyfix.jvm;

import java.lang.reflect.Field;

@SuppressWarnings("unused")
public class ModifiersStub {
    private int modifiers;

    private ModifiersStub() {
    }

    public static Field field() {
        try {
            return ModifiersStub.class.getDeclaredField("modifiers");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isModifiersField(Field field) {
        return field.getDeclaringClass() == ModifiersStub.class
            || (field.getDeclaringClass() == Field.class && "modifiers".equals(field.getName()));
    }

    public static int read(Field target) {
        return FinalFieldRemover.modifiers(target);
    }
}
