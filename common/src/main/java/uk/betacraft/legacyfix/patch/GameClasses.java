package uk.betacraft.legacyfix.patch;

import java.lang.reflect.Modifier;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.NotFoundException;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class GameClasses {
    private static String minecraftAppletClass = null;
    private static String mouseHelperClass = null;
    private static String minecraftClass = null;

    private static String appletModeFieldName = null;
    private static String minecraftFieldName = null;

    public static String findMinecraftAppletClass(PatchPool patchPool) {
        if (minecraftAppletClass != null) {
            return minecraftAppletClass;
        }

        String[] typicalPaths = new String[]{
            "com.mojang.minecraft.MinecraftApplet",
            "net.minecraft.client.MinecraftApplet"
        };

        for (String path : typicalPaths) {
            CtClass cls = patchPool.getRawClass(path);
            if (cls != null) {
                minecraftAppletClass = cls.getName();
                break;
            }
        }

        return minecraftAppletClass;
    }

    public static String findMinecraftClass(PatchPool patchPool) throws NotFoundException {
        if (minecraftClass != null) {
            return minecraftClass;
        }

        if (minecraftAppletClass == null) {
            findMinecraftAppletClass(patchPool);
        }

        if (minecraftAppletClass != null) {
            CtClass appletClass = patchPool.getRawClass(minecraftAppletClass);

            for (CtField field : appletClass.getDeclaredFields()) {
                String className = field.getType().getName();

                if (!className.equals("java.awt.Canvas")
                    && !className.equals("java.lang.Thread")
                    && !className.equals("long")
                ) {
                    minecraftClass = className;
                    Logger.debug("Found Minecraft class: " + minecraftClass);
                    break;
                }
            }
        }

        return minecraftClass;
    }

    public static String findMinecraftFieldName(PatchPool patchPool) throws NotFoundException {
        if (minecraftFieldName != null) {
            return minecraftFieldName;
        }

        if (minecraftAppletClass == null) {
            findMinecraftAppletClass(patchPool);
        }

        if (minecraftAppletClass != null) {
            CtClass appletClass = patchPool.getRawClass(minecraftAppletClass);

            for (CtField field : appletClass.getDeclaredFields()) {
                String className = field.getType().getName();

                if (!className.equals("java.awt.Canvas")
                    && !className.equals("java.lang.Thread")
                    && !className.equals("long")
                ) {
                    minecraftFieldName = field.getName();
                    Logger.debug("Found Minecraft field: " + field.getName());
                    return minecraftFieldName;
                }
            }
        }

        return minecraftFieldName;
    }

    public static String findAppletModeFieldName(PatchPool patchPool) throws NotFoundException {
        if (appletModeFieldName != null) {
            return appletModeFieldName;
        }

        if (minecraftClass == null) {
            findMinecraftClass(patchPool);
        }

        if (minecraftClass != null) {
            CtClass mcClass = patchPool.getRawClass(minecraftClass);

            for (CtField field : mcClass.getDeclaredFields()) {
                String className = field.getType().getName();

                if (className.equals("boolean") && Modifier.isPublic(field.getModifiers())) {
                    appletModeFieldName = field.getName();
                    Logger.debug("Found appletMode field: " + appletModeFieldName);
                    break;
                }
            }
        }

        return appletModeFieldName;
    }

    public static String findMouseHelperClass(PatchPool patchPool) throws NotFoundException {
        if (mouseHelperClass != null) {
            return mouseHelperClass;
        }

        if (minecraftClass == null) {
            findMinecraftClass(patchPool);
        }

        if (minecraftClass == null) {
            return null;
        }

        CtClass mcClass = patchPool.getRawClass(minecraftClass);
        CtField[] minecraftFields = mcClass.getDeclaredFields();
        for (CtField field : minecraftFields) {
            CtConstructor[] constructors = field.getType().getConstructors();

            for (CtConstructor constr : constructors) {
                CtClass[] constrParams = constr.getParameterTypes();

                if (constrParams.length >= 1
                    && constrParams[0].getName().equals("java.awt.Component")
                    && !field.getType().getName().equals(minecraftClass)
                ) {
                    mouseHelperClass = field.getType().getName();
                    Logger.debug("Found match for MouseHelper class: " + mouseHelperClass);
                    break;
                }
            }
        }

        return mouseHelperClass;
    }
}