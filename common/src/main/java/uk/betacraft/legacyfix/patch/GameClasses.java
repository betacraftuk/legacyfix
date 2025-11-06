package uk.betacraft.legacyfix.patch;

import java.lang.reflect.Modifier;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.NotFoundException;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class GameClasses {
    private static CtClass minecraftAppletClass = null;
    private static CtClass mouseHelperClass = null;
    private static CtClass minecraftClass = null;

    private static CtField appletModeField = null;
    private static CtField minecraftField = null;

    public static CtClass findMinecraftAppletClass(PatchPool transformer) {
        if (minecraftAppletClass != null) {
            return minecraftAppletClass;
        }

        String[] typicalPaths = new String[]{
            "com.mojang.minecraft.MinecraftApplet",
            "net.minecraft.client.MinecraftApplet"
        };

        for (String path : typicalPaths) {
            minecraftAppletClass = transformer.getClass(path);
            if (minecraftAppletClass != null) {
                break;
            }
        }

        return minecraftAppletClass;
    }

    public static CtClass findMinecraftClass(PatchPool transformer) throws NotFoundException {
        if (minecraftClass != null) {
            return minecraftClass;
        }

        if (minecraftAppletClass == null) {
            findMinecraftAppletClass(transformer);
        }

        if (minecraftAppletClass != null) {
            for (CtField field : minecraftAppletClass.getDeclaredFields()) {
                String className = field.getType().getName();

                if (!className.equals("java.awt.Canvas") &&
                    !className.equals("java.lang.Thread") &&
                    !className.equals("long")) {

                    minecraftClass = field.getType();
                    Logger.debug("Found Minecraft class: " + minecraftClass.getName());
                    break;
                }
            }
        }

        return minecraftClass;
    }

    public static CtField findMinecraftField(PatchPool transformer) throws NotFoundException {
        if (minecraftField != null) {
            return minecraftField;
        }

        if (minecraftAppletClass == null) {
            findMinecraftAppletClass(transformer);
        }

        for (CtField field : minecraftAppletClass.getDeclaredFields()) {
            String className = field.getType().getName();

            if (!className.equals("java.awt.Canvas") &&
                !className.equals("java.lang.Thread") &&
                !className.equals("long")) {

                minecraftField = field;
                Logger.debug("Found Minecraft field: " + field.getName());

                return field;
            }
        }

        return minecraftField;
    }

    public static CtField findAppletModeField(PatchPool transformer) throws NotFoundException {
        if (appletModeField != null) {
            return appletModeField;
        }

        if (minecraftClass == null) {
            findMinecraftClass(transformer);
        }

        for (CtField field : minecraftClass.getDeclaredFields()) {
            String className = field.getType().getName();

            if (className.equals("boolean") && Modifier.isPublic(field.getModifiers())) {
                appletModeField = field;

                Logger.debug("Found appletMode field: " + appletModeField.getName());
                break;
            }
        }

        return appletModeField;
    }

    public static CtClass findMouseHelperClass(PatchPool transformer) throws NotFoundException {
        if (mouseHelperClass != null) {
            return mouseHelperClass;
        }

        if (minecraftClass == null) {
            findMinecraftClass(transformer);
        }

        if (minecraftClass == null) {
            return null;
        }

        CtField[] minecraftFields = minecraftClass.getDeclaredFields();
        for (CtField field : minecraftFields) {
            CtConstructor[] constructors = field.getType().getConstructors();

            for (CtConstructor constr : constructors) {
                CtClass[] constrParams = constr.getParameterTypes();

                if (constrParams.length >= 1 &&
                    constrParams[0].getName().equals("java.awt.Component") &&
                    !field.getType().getName().equals(minecraftClass.getName())) {
                    mouseHelperClass = field.getType();

                    Logger.debug("Found match for MouseHelper class: " + mouseHelperClass.getName());
                    break;
                }
            }
        }

        return mouseHelperClass;
    }
}