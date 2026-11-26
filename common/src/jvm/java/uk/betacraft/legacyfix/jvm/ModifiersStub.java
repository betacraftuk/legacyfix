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

    public static Field[] withModifiers(Object declaredFields) {
        Field[] fields = (Field[]) declaredFields;
        for (Field field : fields) {
            if ("modifiers".equals(field.getName())) {
                return fields;
            }
        }

        Field[] result = new Field[fields.length + 1];
        System.arraycopy(fields, 0, result, 0, fields.length);
        result[fields.length] = field();
        return result;
    }

    public static boolean isModifiersField(Field field) {
        return field != null && field.getDeclaringClass() == ModifiersStub.class
            || (field.getDeclaringClass() == Field.class && "modifiers".equals(field.getName()));
    }

    public static int read(Field target) {
        return FinalFieldRemover.modifiers(target);
    }
}
