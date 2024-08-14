package uk.betacraft.legacyfix.patch;

import javassist.ClassPool;
import uk.betacraft.legacyfix.LegacyFixAgent;

import java.lang.instrument.Instrumentation;

public abstract class Patch {
    private final String id, description;
    private final Object setting;
    private final boolean isDefault;
    protected static final ClassPool pool = ClassPool.getDefault();

    /**
     * @param id The ID of the patch. Formatted with camelCase.
     * @param description A brief description of the patch.
     * @param isDefault Whether this patch is enabled by default. Adds a disable option.
     */
    public Patch(String id, String description, boolean isDefault) {
        this.id = id;
        this.description = description;
        this.isDefault = isDefault;
        this.setting = LegacyFixAgent.getSettings().get("lf." + getId() + (isDefault ? ".disable" : ""));
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

    /**
     * Conditions for the patch to be applied.
     * @return If the patch should be applied
     */
    @SuppressWarnings("all")
    public boolean shouldApply() {
        return setting != null;
    }

    /**
     * Applies the patch. Should only ever be called in the agent's premain.
     * @throws PatchException Exceptions thrown by the patches
     * @throws Exception Other exceptions, usually related to class patching
     */
    public abstract void apply(final Instrumentation inst) throws PatchException, Exception;
}
