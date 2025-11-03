package uk.betacraft.legacyfix.patch;

import javassist.ClassPool;
import javassist.CtClass;
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
    public boolean shouldApply(PatchTransformer transformer) {
        return required || (def ? this.getSetting() == null : this.getSetting() != null);
    }

    /**
     * Applies the patch.
     *
     * @param transformer     The patch transformer for ClassNode transformations
     * @throws PatchException Exceptions thrown by the patches
     * @throws Exception      Other exceptions, usually related to class patching
     */
    public abstract void apply(PatchTransformer transformer) throws PatchException, Exception;

    // Helper utilities
    public static CtClass ctString = ClassPool.getDefault().getOrNull("java.lang.String");
    public static String asLoadClass(String className) {
        return "$0.getClass().getClassLoader().loadClass(\"" + className + "\")";
    }
}
