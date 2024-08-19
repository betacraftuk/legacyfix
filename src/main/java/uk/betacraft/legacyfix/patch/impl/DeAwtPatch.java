package uk.betacraft.legacyfix.patch.impl;

import uk.betacraft.legacyfix.patch.Patch;

import java.lang.instrument.Instrumentation;

public class DeAwtPatch extends Patch {
    public DeAwtPatch() {
        super("deawt", "Forces the game to use LWJGL's Display instead of AWT's Frame", true);
    }

    @Override
    public void apply(final Instrumentation inst) throws Exception  {
        
    }
}
