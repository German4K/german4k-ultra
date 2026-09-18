# German4K Ultra

German4K Ultra is the TV/Android player of **German 4K** (german4k.com), built on the open-source
player [OwnTV](https://github.com/ahXN00/OwnTV) by ahXN00 and its engine
[OwnTV_Core](https://github.com/ahXN00/OwnTV_Core). Both are licensed under the
**GNU General Public License v3.0**; this fork is published under the same licence (see `LICENSE`).

What this fork changes:

- **Zero-setup provisioning** — on first start the app asks the German4K panel
  (`https://german4k.com/api/app/ultra`) which access belongs to this device and imports the
  sources itself. Three pairing ways: sign in on the TV, QR code to the website, or the
  German4K team pairs the device. See `core/german4k/` in the core fork
  ([german4k-ultra-core](https://github.com/German4K/german4k-ultra-core)).
- **White-label branding** — name, icons, colours and texts are German4K; the About screen keeps
  the OwnTV/GPL attribution and links back here.
- German and British English are the packaged languages.

Build: clone both forks side by side, put `owntv.corePath=<path to german4k-ultra-core>` into
`~/.gradle/gradle.properties`, then `./gradlew :app:assembleStandardRelease` (JDK 21, AGP 9.4).

Nothing in this repository contains credentials or panel secrets; the app only knows the public
API endpoint that every installed copy calls anyway.
