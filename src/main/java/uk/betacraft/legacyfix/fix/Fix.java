package uk.betacraft.legacyfix.fix;

import javassist.ClassPool;
import uk.betacraft.legacyfix.LegacyFixAgent;

import java.lang.instrument.Instrumentation;

public class Fix {
    private final String id, name;
    private final Object setting;
    protected static final ClassPool pool = ClassPool.getDefault();

    public Fix(String id, String name) {
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
     * Conditions for the fix to be applied. Usually JVM version checks if overriden.
     * @return If the fix should be applied
     */
    public boolean shouldApply() {
        return setting != null;
    }

    /**
     * Applies the fix. Should only ever be called in the agent's premain.
     * @return If the fix was correctly applied
     */
    public boolean apply(final Instrumentation inst) {
        return true;
    }


}
