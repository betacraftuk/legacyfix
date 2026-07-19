<h1 align="center">LegacyFix</h1>

![LegacyFix banner](/.github/img/banner.webp)

---

## Downloads

### Stable releases
Go to Releases and download the [latest version of LegacyFix](https://github.com/betacraftuk/legacyfix/releases/latest).

### Nightly builds
Find the latest (topmost) run in the [Actions tab](https://github.com/betacraftuk/legacyfix/actions/workflows/gradle.yml).
Scroll down to the **Artifacts** section and download the zip file, in which you can find the LegacyFix jar.

Note: If you don't have a GitHub account, download the zip [here](https://nightly.link/betacraftuk/legacyfix/workflows/gradle/v3).

## Installation

### Forge
LegacyFix acts as a core mod. Depending on the game version:
* **For Minecraft 1.3 or older**: Follow [Other](#other).
* **For Minecraft 1.4**: Drop the `.jar` file into your `.minecraft/coremods` folder.
* **For Minecraft 1.5 or newer**: Drop the `.jar` file into your `.minecraft/mods` folder.

### Fabric
LegacyFix can be installed as a standard mod. Simply drop the `.jar` file into your `.minecraft/mods` folder.

All Fabric-based loaders (Legacy Fabric, Babric, Ornithe, etc.) are supported.

### Other
If you are playing Vanilla or using an older mod loader (like Risugami's ModLoader), you can run LegacyFix as a Java Agent.
* [Tutorial for Prism Launcher](docs/Prism%20Launcher.md)
* [Tutorial for MultiMC](docs/MultiMC.md)

*Note: If you're using the [Betacraft v2 Launcher](https://github.com/betacraftuk/betacraft-launcher/tree/v2), LegacyFix is included by default*

---

## Patches

### Proxy
- Online mode multiplayer auth
- Skins and capes (with [override support](docs/Skin%20overrides.md))
- Accurate version-specific assets and sounds

### Graphics
- `bitdepth`: Torn clouds on AMD GPUs
- `a1.1.1`: Gray screen crash in Alpha v1.1.1
- `intel`: Rendering bugs on Intel GPUs (b1.9 - 1.7.10)
- `screenshot`: Broken screenshots after resizing the game window

### Windowing
- `vsync`: Forces V-Sync
- `deawt`: Flipped colors on Apple Silicon Macs
- `classic-resize`: Fixes window resizing in Classic and early Indev

### Input & Audio
- `mouse`: Modernizes mouse input handling
- `indev-sound`: Restores missing sound in early Indev
- `disable-controllers`: Disables buggy controller support that freezes the startup screen

### Mod Compatibility
- `beta-forge`: Fixes Beta Forge (b1.7.3 - b1.8.1) crashing on Java 9+

### Misc
- `game-dir`: Isolates the game directory for ancient versions
- `java-modules`: Unlocks internal modules (required for Java 11+)
- `texture-pack-folder`: Fixes the broken "Open Texture Pack Folder" button on Mac/Linux

Information about additional settings can be found [here](docs/Additional%20settings.md).