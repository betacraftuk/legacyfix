package uk.betacraft.legacyfix.patch;

import javassist.ClassPool;
import uk.betacraft.legacyfix.LegacyFixAgent;

import java.lang.instrument.Instrumentation;

@SuppressWarnings("unused")
public abstract class Patch {
    private final String id, description;
    private final boolean isDefault;
    private final boolean isRequired;

    private Object setting;

    protected static final ClassPool pool = ClassPool.getDefault();

    /**
     * @param id          The ID of the patch. Formatted with camelCase.
     * @param description A brief description of the patch.
     * @param isDefault   Whether this patch is enabled by default. Adds a disable option.
     * @param isRequired  Whether this patch is required for the game to run.
     *                    The patch cannot be disabled and the game will crash if it fails to apply.
     */
    public Patch(String id, String description, boolean isDefault, boolean isRequired) {
        this.id = id;
        this.description = description;
        this.isDefault = isDefault;
        this.isRequired = false;
    }

    public Patch(String id, String description, boolean isDefault) {
        this(id, description, isDefault, false);
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public Object getSetting() {
        if (this.setting == null)
            this.setting = LegacyFixAgent.getSettings().get("lf." + getId() + (isDefault ? ".disable" : ""));

        return this.setting;
    }

    /**
     * Conditions for the patch to be applied.
     *
     * @return If the patch should be applied
     */
    @SuppressWarnings("all")
    public boolean shouldApply() {
        return isRequired || (isDefault ? this.getSetting() == null : this.getSetting() != null);
    }

    /**
     * Applies the patch. Should only ever be called in the agent's premain.
     *
     * @throws PatchException Exceptions thrown by the patches
     * @throws Exception      Other exceptions, usually related to class patching
     */
    public abstract void apply(final Instrumentation inst) throws PatchException, Exception;
}
