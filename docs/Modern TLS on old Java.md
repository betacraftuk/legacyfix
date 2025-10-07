# Modern TLS on old Java
## What depends on modern TLS when using LegacyFix?
- fetching player skins, capes
- downloading version-correct assets, sounds
- authenticating with multiplayer servers
- authenticating with and connecting to online Classic level servers
## When to install the Bouncy Castle TLS library?
You want to install the TLS library:
- when using Windows 10/11 with Intel HD Graphics available as the sole display adapter, forcing you to use old Java builds for Minecraft to run
- when using an old version of Windows or macOS, unable to install the newest Java 8, e.g.:
  - Windows 95, 98, Me, NT 4.0, 2000, XP, Server 2003, Vista, Server 2008
  - Mac OS X 10.4, 10.5, 10.6, 10.7, 10.8, 10.9, 10.10, 10.11, 10.12
## Installing the Bouncy Castle TLS library in MultiMC and Prism Launcher
1. Download [bcprov-jdk15to18-1.82.jar](https://downloads.bouncycastle.org/java/bcprov-jdk15to18-1.82.jar), [bcutil-jdk15to18-1.82.jar](https://downloads.bouncycastle.org/java/bcutil-jdk15to18-1.82.jar) and [bctls-jdk15to18-1.82.jar](https://downloads.bouncycastle.org/java/bctls-jdk15to18-1.82.jar)
2. Edit your instance in MultiMC/Prism, go to the **Version** tab and click on `Add to minecraft.jar`
3. Select all three jars you have downloaded in step 1 and confirm adding them. Once that's done LegacyFix will automatically start using the library when you run the game.

Optionally, if you are running modern-TLS-capable Java, you can force LegacyFix to use Bouncy Castle with this JVM argument: `-Dlf.bouncycastle`