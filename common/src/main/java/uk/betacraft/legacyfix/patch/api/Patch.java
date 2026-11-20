package uk.betacraft.legacyfix.patch.api;

import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.expr.ExprEditor;
import uk.betacraft.legacyfix.Agent;

@SuppressWarnings("unused")
public abstract class Patch {
    private final String id, description;
    private final boolean def;
    private final boolean required;

    private Object setting;

    /**
     * @param id          The id of the patch, formatted with camelCase.
     * @param description A brief description of the patch.
     * @param def         If this patch is enabled by default. Adds a disable option.
     * @param required    Whether this patch, if applicable, is required for the game to run.
     *                    The patch can't be disabled and the game will crash if transforming fails.
     */
    public Patch(String id, String description, boolean def, boolean required) {
        this.id = id;
        this.description = description;
        this.def = def;
        this.required = required;
    }

    public Patch(String id, String description, boolean def) {
        this(id, description, def, false);
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDefault() {
        return def;
    }

    public boolean isRequired() {
        return required;
    }

    public Object getSetting() {
        if (this.setting == null) {
            this.setting = Agent.getSettings().get("lf." + getId() + (isDefault() ? ".disable" : ""));
        }

        return this.setting;
    }

    /**
     * @return If the patch should be applied
     */
    @SuppressWarnings("all")
    public boolean shouldApply(PatchPool patchPool) {
        return isRequired() || (isDefault() ? this.getSetting() == null : this.getSetting() != null);
    }

    /**
     * Applies the patch.
     *
     * @param patchPool       The PatchPool for class patching/retrieval
     * @throws PatchException Exceptions thrown by the patches directly
     * @throws Exception      Other exceptions, usually related to class patching
     */
    public abstract void apply(PatchPool patchPool) throws PatchException, Exception;

    // Helper utilities
    public static final CtClass CT_INT = CtClass.intType;
    public static final CtClass CT_LONG = CtClass.longType;
    public static final CtClass CT_FLOAT = CtClass.floatType;
    public static final CtClass CT_BOOLEAN = CtClass.booleanType;
    public static final CtClass CT_STRING = ClassPool.getDefault().getOrNull("java.lang.String");

    public static CtClass ctFromBytes(byte[] classBytes) throws Exception {
        return new ClassPool().makeClass(new java.io.ByteArrayInputStream(classBytes));
    }

    public static CtClass ctFromBytes(String className, byte[] classBytes) throws NotFoundException {
        ClassPool pool = new ClassPool(true);
        pool.insertClassPath(new ByteArrayClassPath(className, classBytes));
        return pool.get(className);
    }

    public static byte[] instrument(String className, byte[] classBytes, ExprEditor editor) throws Exception {
        CtClass ctClass = ctFromBytes(className, classBytes);
        try {
            boolean modified = false;
            for (MethodInfo method : ctClass.getClassFile().getMethods()) {
                modified |= editor.doit(ctClass, method);
            }
            return modified ? ctClass.toBytecode() : null;
        } finally {
            ctClass.detach();
        }
    }

    public static String invokeStatic(String className, String methodName, String[] paramTypes, String[] args) {
        return "java.lang.Class.forName(\"" + className + "\", true, java.lang.Thread.currentThread().getContextClassLoader())" +
            ".getMethod(\"" + methodName + "\", new java.lang.Class[] {" + join(paramTypes) + "})" +
            ".invoke(null, new java.lang.Object[] {" + join(args) + "})";
    }

    private static String join(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    public static boolean mentions(byte[] bytecode, String needle) {
        byte[] needleBytes = needle.getBytes();
        outer:
        for (int i = 0; i <= bytecode.length - needleBytes.length; i++) {
            for (int j = 0; j < needleBytes.length; j++) {
                if (bytecode[i + j] != needleBytes[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean isString(ConstPool constPool, int ldcPos) {
        return constPool.getTag(ldcPos) == ConstPool.CONST_String;
    }

    public static boolean isUtf8(ConstPool constPool, int ldcPos) {
        return constPool.getTag(ldcPos) == ConstPool.CONST_Utf8;
    }
}
