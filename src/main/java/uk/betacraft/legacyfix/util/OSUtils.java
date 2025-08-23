package uk.betacraft.legacyfix.util;

public class OSUtils {
    public enum OS {
        WINDOWS,
        LINUX,
        MACOS,
        OTHER;
    }

    public enum Arch {
        X86_64,
        X86_32,
        AARCH64,
        OTHER;
    }

    public static class Platform {
        private final OS os;
        private final Arch arch;

        public Platform(OS os, Arch arch) {
            this.os = os;
            this.arch = arch;
        }

        public OS getOS() {
            return this.os;
        }

        public Arch getArch() {
            return this.arch;
        }

        public boolean is(OS os, Arch arch) {
            return this.os == os && this.arch == arch;
        }
    }

    public static Platform getPlatform() {
        return new Platform(getOS(), getArch());
    }

    public static OS getOS() {
        String os_name = System.getProperty("os.name").toLowerCase();
        if (os_name.contains("windows")) {
            return OS.WINDOWS;
        } else if (os_name.contains("linux")) {
            return OS.LINUX;
        } else if (os_name.contains("mac")) {
            return OS.MACOS;
        } else {
            return OS.OTHER;
        }
    }

    public static Arch getArch() {
        // for our usage, this way of checking architecture is enough
        String os_arch = System.getProperty("os.arch").toLowerCase();
        if (os_arch.equals("amd64")) {
            return Arch.X86_64;
        } else if (os_arch.equals("x86")) {
            return Arch.X86_32;
        } else if (os_arch.equals("aarch64")) {
            return Arch.AARCH64;
        } else {
            return Arch.OTHER;
        }
    }
}
