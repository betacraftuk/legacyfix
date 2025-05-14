# Additional settings of LegacyFix
Settings of LegacyFix can be altered through Java arguments.
## Debug messages
```
-Dlf.debug
```
## Level server for online saving in Classic
```
-Dlf.levelServer=<server address>
```
## VSync
```
-Dlf.vsync
```
## Run Infinite Map Visualizer
Introduced with inf-20100616, forgotten with a1.2.0, and completely dysfunctional with b1.3, Infinite Map Visualizer lets you look at your worlds from a bird's eye view.
```
-Dlf.visualizer
```
## Disable deleting the `resources` folder when using Prism/MultiMC
Prism Launcher and MultiMC use inferior Mojang asset indexes for versions before 1.7.3.<br>
They copy the assets into the `resources` directory located in the instance directory each time you launch the game.<br>
Not only is it a waste of disk space (especially if you have many instances), but those assets are inaccurate for versions before 1.0.0-rc1.<br>
That's why LegacyFix clears the folder on each launch* by default, so that those inaccurate assets are not duplicated and are not used by the game.<br>
However, if for some reason you want not to clear the `resources` directory (such as for mods that have custom sounds, or if you want to replace some default sounds), you can use the setting below.<br>
<br>
\* The moment you first ran LegacyFix with your Prism/MultiMC instance, it replaced the inaccurate asset index used by the launcher for that instance to the accurate one (one that also doesn't copy assets into `resources`).
So if you apply this setting <ins>_after_</ins> you've run your instance with LegacyFix at least once, your launcher will not force the inaccurate assets onto the game, and you will be able to add or override game sounds.
```
-Dlf.keep-resources
```
## Disable patching `net.minecraft.json` when using Prism/MultiMC
Disables replacing inaccurate asset index with the accurate one.<br>
See setting above for more information (2nd section).
```
-Dlf.keep-net.minecraft.json
```
## Disable patching `org.lwjgl.json` when using Prism on Apple Silicon devices
Disables replacing LWJGL natives with ones that don't crash when resizing the game window.<br>
LegacyFix won't patch this file for MultiMC as MultiMC does not properly support Apple Silicon devices.
```
-Dlf.keep-org.lwjgl.json
```
## "Minecraft 1.6 is out" notice in 1.5.2
```
-Dlf.showNotice
```
## Invert mouse vertical movement
Has the same effect as **Invert mouse: ON** in game options
```
-Dlf.mouse=invert
```
## Java 9+ support for Risugami's ModLoader
```
-Dlf.modloader
```
## Java 9+ support for Forge (b1.7.3 - b1.8.1)
```
-Dlf.beta-forge
```
## Disable default patches
```
-Dlf.<patch-id>.disable
```