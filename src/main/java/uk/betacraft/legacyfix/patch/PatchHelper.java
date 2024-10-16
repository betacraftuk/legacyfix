package uk.betacraft.legacyfix.patch;

import java.io.File;
import java.lang.reflect.Modifier;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.ConstPool;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.LegacyFixLauncher;

public class PatchHelper {
    public static final CtClass stringClass = ClassPool.getDefault().getOrNull("java.lang.String");
    public static final CtClass floatClass = ClassPool.getDefault().getOrNull("float");
    public static final CtClass intClass = ClassPool.getDefault().getOrNull("int");

    private static CtClass minecraftAppletClass = null;
    private static CtClass mouseHelperClass = null;
    private static CtClass minecraftClass = null;

    private static CtField appletModeField = null;
    private static CtField minecraftField = null;

    public static CtClass findMinecraftAppletClass(ClassPool pool) {
        if (minecraftAppletClass != null) {
            return minecraftAppletClass;
        }

        String[] typicalPaths = new String[]{"net.minecraft.client.MinecraftApplet", "com.mojang.minecraft.MinecraftApplet"};

        for (String path : typicalPaths) {
            minecraftAppletClass = pool.getOrNull(path);
            if (minecraftAppletClass != null) {
                break;
            }
        }

        return minecraftAppletClass;
    }

    public static CtClass findMinecraftClass(ClassPool pool) throws NotFoundException {
        if (minecraftClass != null) {
            return minecraftClass;
        }

        if (minecraftAppletClass == null) {
            findMinecraftAppletClass(pool);
        }

        minecraftClass = pool.getOrNull("net.minecraft.client.Minecraft");

        if (minecraftClass == null && minecraftAppletClass != null) {
            for (CtField field : minecraftAppletClass.getDeclaredFields()) {
                String className = field.getType().getName();

                if (!className.equals("java.awt.Canvas") &&
                        !className.equals("java.lang.Thread") &&
                        !className.equals("long")) {

                    minecraftClass = field.getType();
                    if (LegacyFixAgent.isDebug())
                        LFLogger.info("Found Minecraft class: " + minecraftClass.getName());

                    break;
                }
            }
        }

        return minecraftClass;
    }

    public static CtField findMinecraftField(ClassPool pool) throws NotFoundException {
        if (minecraftField != null) {
            return minecraftField;
        }

        if (minecraftAppletClass == null) {
            findMinecraftAppletClass(pool);
        }

        for (CtField field : minecraftAppletClass.getDeclaredFields()) {
            String className = field.getType().getName();

            if (!className.equals("java.awt.Canvas") &&
                    !className.equals("java.lang.Thread") &&
                    !className.equals("long")) {

                minecraftField = field;
                if (LegacyFixAgent.isDebug())
                    LFLogger.info("Found Minecraft field: " + field.getName());

                return field;
            }
        }

        return minecraftField;
    }

    public static CtField findAppletModeField(ClassPool pool) throws NotFoundException {
        if (appletModeField != null) {
            return appletModeField;
        }

        if (minecraftClass == null) {
            findMinecraftClass(pool);
        }

        for (CtField field : minecraftClass.getDeclaredFields()) {
            String className = field.getType().getName();

            if (className.equals("boolean") && Modifier.isPublic(field.getModifiers())) {
                appletModeField = field;

                if (LegacyFixAgent.isDebug())
                    LFLogger.info("Found appletMode field: " + appletModeField.getName());

                break;
            }
        }

        return appletModeField;
    }

    public static CtClass findMouseHelperClass(ClassPool pool) throws NotFoundException {
        if (mouseHelperClass != null) {
            return mouseHelperClass;
        }

        if (minecraftClass == null) {
            findMinecraftClass(pool);
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

                    if (LegacyFixAgent.isDebug())
                        LFLogger.info("Found match for MouseHelper class: " + mouseHelperClass.getName());

                    break;
                }
            }
        }

        return mouseHelperClass;
    }

    // Used by GameDirPatch
    public static File getIndevMapRenderFromExpectedPath(File file) {
        String fileName = file.getName();
        File expectedFile = new File(new File(System.getProperty("user.home", ".")), fileName).getAbsoluteFile();

        if (!fileName.startsWith("mc_map_") ||
                !fileName.endsWith(".png") ||
                !expectedFile.getPath().equals(file.getAbsoluteFile().getPath())) {
            return null;
        }

        return new File(LegacyFixLauncher.getScreenshotsDir(), fileName).getAbsoluteFile();
    }

    // Bytecode manipulation helper methods
    public static boolean isString(ConstPool constPool, int ldcPos) {
        return constPool.getTag(ldcPos) == ConstPool.CONST_String;
    }

    public static boolean isUtf8(ConstPool constPool, int ldcPos) {
        return constPool.getTag(ldcPos) == ConstPool.CONST_Utf8;
    }
}
