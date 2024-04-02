package uk.betacraft.legacyfix.fix.impl;

import uk.betacraft.legacyfix.fix.Fix;

import java.lang.instrument.Instrumentation;

public class DeAwtFix extends Fix {
    public DeAwtFix() {
        super("deawt", "DeAWT");
    }

    @Override
    public boolean apply(final Instrumentation inst) {
        return true;
    }
}
