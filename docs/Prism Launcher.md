# Using LegacyFix with Prism Launcher
## Grab the latest artifact from GitHub Action
Head over to the [Actions tab](https://github.com/betacraftuk/legacyfix/actions/workflows/gradle.yml?query=branch%3Adevelop) and click on the latest run (at the top).<br>
Then, scroll down to the **Artifacts** section and download the `artifact.zip` file,<br>
in it should be a file called `legacyfix-2.0.jar`.

Note: Downloading artifacts requires a GitHub account, though you can use [nightly.link](https://nightly.link/) to download the latest build without one.
By clicking [here](https://nightly.link/betacraftuk/legacyfix/workflows/gradle/develop/artifact.zip) you can download the latest build via nightly.link.

## Add LegacyFix to Prism Launcher
> [!IMPORTANT]
> Make sure `Enable online fixes` is disabled in the instance settings, as LegacyFix does not work with it.
>
> You can do this by editing the instance, go to `Settings` -> `Miscellaneous`, and then you will see a section called `Legacy settings`, and there you can turn off `Enable online fixes (experimental)`.

Create a new instance or edit an existing one, then go to the **Version** tab.<br>
On the sidebar, click the **Add Agents** button:
![](../.github/img/prism/add-agents.webp)
And point the launcher to the `legacyfix-2.0.jar` file you downloaded earlier:
![](../.github/img/prism/file-picker.webp)
A new entry should appear in the list:
![](../.github/img/prism/lf-agent-entry.webp)

#### For LegacyFix to function properly, you should also make your instance use up-to-date Java.
Prism Launcher defaults to outdated Java 8u51 (from July 2015) on Windows, 8u202 (from January 2019) on Linux, and 8u74 (from February 2016) on Intel macOS.
<br>If you're using Windows 10/11 with Intel HD Graphics and <ins>*you know*</ins> that you need to be using Java 8u51 for Minecraft to run, read [this document](Modern%20TLS%20on%20old%20Java.md).
<br>Otherwise, follow these steps to get a recent build of Java 8:

Edit your instance, go to the **Settings** tab, then select the "Java installation" checkbox:
![](../.github/img/prism/java-instance-settings.webp)

Next, click on the **Download Java** button, pick Adoptium or Azul Zulu (we recommend Azul Zulu), and select the most recent release of Java 8:

![](../.github/img/prism/java-instance-download.webp)

Then click on **Download** and wait for it to finish.

Next, click the **Auto-detect** button and select the Java installation you've just downloaded and click **Ok**:
![](../.github/img/prism/java-instance-select.webp)

And it's done! You're ready to play Minecraft with LegacyFix.
