package uk.betacraft.legacyfix.patch;

import javassist.ClassPool;
import uk.betacraft.legacyfix.LegacyFixAgent;

import java.lang.instrument.Instrumentation;

public abstract class Patch {
    private final String id, name;
    private final Object setting;
    protected static final ClassPool pool = ClassPool.getDefault();

    public Patch(String id, String name) {
        this.id = id;
        this.name = name;
        this.setting = LegacyFixAgent.getSettings().get("lf." + getId());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Conditions for the patch to be applied. Usually JVM version checks if overridden.
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
