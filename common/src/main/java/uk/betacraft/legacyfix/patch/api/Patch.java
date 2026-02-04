package uk.betacraft.legacyfix.patch.api;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.ConstPool;
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
            this.setting = Agent.getSettings().get("lf." + getId() + (def ? ".disable" : ""));
        }

        return this.setting;
    }

    /**
     * @return If the patch should be applied
     */
    @SuppressWarnings("all")
    public boolean shouldApply(PatchPool patchPool) {
        return required || (def ? this.getSetting() == null : this.getSetting() != null);
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
    public static final CtClass CT_FLOAT = CtClass.floatType;
    public static final CtClass CT_STRING = ClassPool.getDefault().getOrNull("java.lang.String");

    public static CtClass ctFromBytes(byte[] classBytes) throws Exception {
        return new ClassPool().makeClass(new java.io.ByteArrayInputStream(classBytes));
    }

    public static boolean isString(ConstPool constPool, int ldcPos) {
        return constPool.getTag(ldcPos) == ConstPool.CONST_String;
    }

    public static boolean isUtf8(ConstPool constPool, int ldcPos) {
        return constPool.getTag(ldcPos) == ConstPool.CONST_Utf8;
    }
}
