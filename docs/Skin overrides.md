# Skin overrides
## Modes
You can set the mode with the `-Dlf.proxy.overrides.mode=<mode>` JVM argument.

Available modes:
* **`mixed` (default)**: Overrides are loaded from local files first. If a local file is not found, the remote API is used
* `api`: Overrides are exclusively loaded from the remote API
* `local`: Overrides are exclusively loaded from local files
* `disabled`: Overrides are completely disabled

*(Note: You can also disable overrides with `-Dlf.proxy.overrides.disable`)*

## Local overrides
You need to place a `.png` file in a specific folder inside your game directory (`.minecraft`).

The file must be named after the player's username (case-sensitive):
* Skins: `.minecraft/legacyfix/skin/<username>.png`
* Capes: `.minecraft/legacyfix/cape/<username>.png`

For example, to override a skin for the username `Notch`, place the custom skin at `.minecraft/legacyfix/skin/Notch.png`.

## Remote overrides
By default, LegacyFix fetches remote skin and cape overrides from [Betacraft](https://betacraft.uk/).

If you want to use your own host for skins and capes, you can change the API URL with the
`-Dlf.proxy.overrides.host=https://example.com` JVM argument.

Endpoints:
* Skins: `/api/skin/<username>`
* Capes: `/api/cape/<username>`
