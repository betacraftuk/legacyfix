# Using LegacyFix with Prism Launcher
## Grab the latest artifact from GitHub Action
Head over to the [Actions tab](https://github.com/betacraftuk/legacyfix/actions/workflows/gradle.yml) and click on the latest run (at the top).<br>
Then, scroll down to the **Artifacts** section and download the `artifact.zip` file,<br>
in it should be a file called `legacyfix-2.0.jar`.

Note: Downloading artifacts requires a GitHub account, though you can use [nightly.link](https://nightly.link/) to download the latest build without one.

## Add LegacyFix to Prism Launcher
Create a new instance or edit an existing one, then go to the **Version** tab.<br>
On the sidebar, click the **Add Agents** button:
![](.github/img/prism/add-agents.webp)
And point the launcher to the `legacyfix-2.0.jar` file you downloaded earlier:
![](.github/img/prism/file-picker.webp)
A new entry should appear in the list:
![](.github/img/prism/lf-agent-entry.webp)