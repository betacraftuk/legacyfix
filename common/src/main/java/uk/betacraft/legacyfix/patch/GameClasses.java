package uk.betacraft.legacyfix.patch;

import java.lang.reflect.Modifier;

import javassist.*;
import javassist.bytecode.*;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class GameClasses {
    private static String minecraftAppletClass = null;
    private static String mouseHelperClass = null;
    private static String minecraftClass = null;

    private static String appletModeFieldName = null;
    private static String minecraftFieldName = null;
    private static String sessionClassName = null;
    private static String sessionFieldName = null;

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
                }
            }
        } else {
            CtClass clientClass = patchPool.getRawClass("net.minecraft.client.Minecraft");
            if (clientClass != null) {
                minecraftClass = clientClass.getName();
            } else {
                try {
                    minecraftClass = findMinecraftFromMain(patchPool);
                } catch (Exception e) {
                    Logger.error("GameClasses", e);
                }
            }
        }

        if (minecraftClass != null) {
            Logger.debug("Minecraft class found: " + minecraftClass);
        }

        return minecraftClass;
    }

    private static String findMinecraftFromMain(PatchPool patchPool) throws NotFoundException, BadBytecode {
        CtClass mainCt = patchPool.getRawClass("net.minecraft.client.main.Main");
        if (mainCt == null) {
            return null;
        }

        CtMethod main = mainCt.getDeclaredMethod("main");
        MethodInfo mi = main.getMethodInfo();
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) {
            return null;
        }

        CodeIterator it = ca.iterator();
        ConstPool cp = mi.getConstPool();

        boolean seenUsername = false;
        String lastNew = null;
        String profile = null;

        while (it.hasNext()) {
            int pos = it.next();
            int op = it.byteAt(pos);

            if (!seenUsername && op == Opcode.LDC) {
                int idx = it.byteAt(pos + 1) & 0xFF;
                if (cp.getTag(idx) == ConstPool.CONST_String) {
                    String s = cp.getStringInfo(idx);
                    if ("username".equals(s)) {
                        seenUsername = true;
                    }
                }
                continue;
            }

            int index = it.u16bitAt(pos + 1);
            if (op == Opcode.NEW) {
                lastNew = cp.getClassInfo(index).replace('/', '.');
                continue;
            }

            if (op == Opcode.INVOKESTATIC) {
                String mClass = cp.getMethodrefClassName(index);
                if ("java.net.Authenticator".equals(mClass)) {
                    profile = null;
                }
            }

            if (op == Opcode.INVOKESPECIAL) {
                String mClass = cp.getMethodrefClassName(index);
                String mName = cp.getMethodrefName(index);
                String mDesc = cp.getMethodrefType(index);
                if ("<init>".equals(mName) && lastNew != null && seenUsername && profile == null) {
                    if (mClass.equals(lastNew)
                        && mDesc.contains("Ljava/lang/String;Ljava/lang/String;")
                    ) {
                        profile = mClass;
                        lastNew = null;
                        continue;
                    }
                }

                if ("<init>".equals(mName) && profile != null) {
                    if (mDesc.contains("L" + profile + ";")) {
                        if (mClass.contains("$")) { // 1.8+
                            profile = mClass.split("\\$")[0];
                            continue;
                        }

                        return mClass;
                    }
                }
            }
        }

        return null;
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

    public static String findSessionClassName(PatchPool patchPool) throws NotFoundException, BadBytecode {
        if (sessionClassName != null) {
            return sessionClassName;
        }

        findSessionFieldName(patchPool);
        return sessionClassName;
    }

    public static String findSessionFieldName(PatchPool patchPool) throws NotFoundException, BadBytecode {
        if (sessionFieldName != null) {
            return sessionFieldName;
        }

        if (minecraftAppletClass == null) {
            findMinecraftAppletClass(patchPool);
        }

        if (minecraftClass == null) {
            findMinecraftClass(patchPool);
        }

        if (minecraftAppletClass == null || minecraftClass == null) {
            return null;
        }

        CtClass appletClass = patchPool.getRawClass(minecraftAppletClass);
        CtMethod init = appletClass.getDeclaredMethod("init");
        MethodInfo mi = init.getMethodInfo();
        CodeAttribute ca = mi.getCodeAttribute();
        if (ca == null) {
            return null;
        }

        CodeIterator it = ca.iterator();
        ConstPool cp = mi.getConstPool();

        boolean seenUsername = false;
        boolean seenSessionId = false;
        String constructedSession = null;

        while (it.hasNext()) {
            int pos = it.next();
            int op = it.byteAt(pos);

            if (op == Opcode.LDC || op == Opcode.LDC_W) {
                int index = op == Opcode.LDC ? it.byteAt(pos + 1) & 0xFF : it.u16bitAt(pos + 1);
                if (cp.getTag(index) == ConstPool.CONST_String) {
                    String value = cp.getStringInfo(index);
                    if ("username".equals(value)) {
                        seenUsername = true;
                    } else if (seenUsername && "sessionid".equals(value)) {
                        seenSessionId = true;
                    }
                }
                continue;
            }

            if (!seenSessionId) {
                continue;
            }

            if (op == Opcode.INVOKESPECIAL) {
                int index = it.u16bitAt(pos + 1);
                String methodName = cp.getMethodrefName(index);
                String methodType = cp.getMethodrefType(index);
                if ("<init>".equals(methodName)
                    && "(Ljava/lang/String;Ljava/lang/String;)V".equals(methodType)
                ) {
                    constructedSession = cp.getMethodrefClassName(index);
                }
                continue;
            }

            if (op == Opcode.PUTFIELD && constructedSession != null) {
                int index = it.u16bitAt(pos + 1);
                String fieldClass = cp.getFieldrefClassName(index);
                String fieldType = cp.getFieldrefType(index);
                if (minecraftClass.equals(fieldClass)
                    && ("L" + constructedSession.replace('.', '/') + ";").equals(fieldType)
                ) {
                    sessionClassName = constructedSession.replace('/', '.');
                    sessionFieldName = cp.getFieldrefName(index);
                    Logger.debug("Found session field: " + sessionFieldName);
                    Logger.debug("Found session class: " + sessionClassName);
                    return sessionFieldName;
                }
            }
        }

        return null;
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
