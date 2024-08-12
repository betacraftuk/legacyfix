package uk.betacraft.legacyfix.patch.impl;

import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;

import java.lang.instrument.Instrumentation;

public class DeAwtPatch extends Patch {
    public DeAwtPatch() {
        super("deawt", "DeAWT");
    }

    @Override
    public void apply(final Instrumentation inst) {
    }

    @Override
    public boolean shouldApply() {
        return !LegacyFixAgent.getSettings().containsKey("lf.deawt.disable");
    }
}
