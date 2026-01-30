<div align="center">
<h1>LegacyFix</h1>
<span>Utility made to patch old and misbehaving versions of Minecraft.</span>
</div>
<br>

![](/.github/img/banner.webp)

## Patches
- [x] Skins
- [x] Sound (correct sounds for every version)
- [x] Online mode support (multiplayer authentication)
- [x] Offline & online saving in Classic versions
- [x] Dynamic resizing in Classic and early Indev versions
- [x] deAWT - Fix for flipped red & blue colors in versions before 13w16a on Apple Silicon devices (M1, M2, etc.)
- [x] BitDepthFix - Fix for glitchy clouds with AMD graphics
- [x] Playing Indev & Infdev versions (also offline)
- [x] Running versions before 13w16a with no dependency on Java AWT/Swing
- [x] Fix for rendering issues in versions b1.9-1.7.10 with Intel graphics
- [x] Fix for running Classic, Indev & Infdev versions on modern macOS
- [x] Fix for c0.0.14a-1.2.5 crashing due to certain USB peripherals
- [x] Fix for "Open texture pack folder" button not being functional before 1.2-pre on Linux and macOS
- [x] Fix for a1.1.1 gray screen
- [x] Fix for sound in early Indev versions
- [x] Fix for running pre-Classic, c0.0.15a-c0.0.16a_02 and b1.3 versions with Java 5
- [x] Fix for b1.7.3 - b1.8.1 Forge when running Java 9+
- [x] Joining servers with c0.0.15a
- [x] Isolated game directory before inf-20100611
- [x] No duplicated assets for every instance
- [x] ModLoader support for Java 9 and later

## Known Issues
- [ ] No Fabric support
- [ ] No Forge support

Support for Fabric and Forge is next in line to come. Monitor the [multiloader](https://github.com/betacraftuk/legacyfix/tree/multiloader) branch for progress.

## Usage
Use the [Betacraft v2 Launcher](https://github.com/betacraftuk/betacraft-launcher/tree/v2) which includes LF by default,<br>
or apply the javaagent in the launcher of your choice:
- [Tutorial for Prism Launcher](docs/Prism%20Launcher.md)
- [Tutorial for MultiMC](docs/MultiMC.md)

Information about additional settings can be found [here](docs/Additional%20settings.md).
