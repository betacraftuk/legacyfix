package uk.betacraft.legacyfix.patch;

import java.lang.reflect.Modifier;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.NotFoundException;
import uk.betacraft.legacyfix.LFLogger;

public class PatchHelper {

    public static CtClass stringClass = ClassPool.getDefault().getOrNull("java.lang.String");
    public static CtClass floatClass = ClassPool.getDefault().getOrNull("float");
    public static CtClass intClass = ClassPool.getDefault().getOrNull("int");

    private static CtClass minecraftAppletClass = null;
    private static CtClass mouseHelperClass = null;
    private static CtClass minecraftClass = null;

    private static CtField appletModeField = null;

    public static CtClass findMinecraftAppletClass(ClassPool pool) {
        if (minecraftAppletClass != null)
            return minecraftAppletClass;

        String[] typicalPaths = new String[] {"net.minecraft.client.MinecraftApplet", "com.mojang.minecraft.MinecraftApplet"};

        for (String path : typicalPaths) {
            minecraftAppletClass = pool.getOrNull(path);

            if (minecraftAppletClass != null)
                break;
        }

        return minecraftAppletClass;
    }

    public static CtClass findMinecraftClass(ClassPool pool) throws NotFoundException {
        if (minecraftClass != null)
            return minecraftClass;

        if (minecraftAppletClass == null)
            findMinecraftAppletClass(pool);

        minecraftClass = pool.getOrNull("net.minecraft.client.Minecraft");

        if (minecraftClass == null) {
            for (CtField field : minecraftAppletClass.getDeclaredFields()) {
                String className = field.getType().getName();

                if (!className.equals("java.awt.Canvas") &&
                        !className.equals("java.lang.Thread") &&
                        !className.equals("long")) {

                    minecraftClass = field.getType();
                    LFLogger.info("Found Minecraft class: " + minecraftClass.getName());
                }
            }
        }

        return minecraftClass;
    }

    public static CtField findAppletModeField(ClassPool pool) throws NotFoundException {
        if (appletModeField != null)
            return appletModeField;

        if (minecraftClass == null)
            findMinecraftClass(pool);

        for (CtField field : minecraftClass.getDeclaredFields()) {
            String className = field.getType().getName();

            if (className.equals("boolean") && Modifier.isPublic(field.getModifiers())) {
                appletModeField = field;

                LFLogger.info("Found appletMode field: " + appletModeField.getName());
                break;
            }
        }

        return appletModeField;
    }

    public static CtClass findMouseHelperClass(ClassPool pool) throws NotFoundException {
        if (mouseHelperClass != null)
            return mouseHelperClass;

        if (minecraftClass == null)
            findMinecraftClass(pool);

        CtField[] minecraftFields = minecraftClass.getDeclaredFields();

        for (CtField field : minecraftFields) {
            CtConstructor[] constructors = field.getType().getConstructors();

            for (CtConstructor constr : constructors) {
                CtClass[] constrParams = constr.getParameterTypes();

                if (constrParams.length >= 1 && constrParams[0].getName().equals("java.awt.Component")) {
                    mouseHelperClass = field.getType();

                    LFLogger.info("Found match for MouseHelper class: " + mouseHelperClass.getName());
                    break;
                }
            }
        }

        return mouseHelperClass;
    }
}
