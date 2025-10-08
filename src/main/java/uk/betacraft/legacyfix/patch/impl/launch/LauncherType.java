package uk.betacraft.legacyfix.patch.impl.launch;

public enum LauncherType {
    PRISM,
    MULTIMC,
    LEGACYFIX,
    LAUNCHWRAPPER,
    UNKNOWN;

    public boolean isMMCBased() {
        return this == MULTIMC || this == PRISM;
    }
}
