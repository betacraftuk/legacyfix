package uk.betacraft.legacyfix.patch.impl;

import uk.betacraft.legacyfix.patch.Patch;

import java.lang.instrument.Instrumentation;

public class DeAwtPatch extends Patch {
    public DeAwtPatch() {
        super("deawt", "DeAWT");
    }

    @Override
    public boolean apply(final Instrumentation inst) {
        return true;
    }
}
