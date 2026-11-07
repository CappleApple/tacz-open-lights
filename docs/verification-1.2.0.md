# TACZ Open Lights 1.2.0 verification

Release builds checked on 2026-11-06: TACZ Open Lights 1.2.0 and Open Lights 2.0.1 for Minecraft 1.20.1 / Forge. No user modpack or world was changed during validation.

## Changes

TaCZ 1.2.0 added attachment render overloads and a default third-person VBO path that bypasses the older attachment traversal. The integration hooks the exact legacy and buffered signatures, prevents duplicate capture through wrapper calls, and captures native external-model bone transforms. The 1.2 classes are resolved lazily so the same integration JAR loads with TaCZ 1.1.8-hotfix. The build uses official Modrinth version `QGCbNkTy`; the upstream release is [TaCZ 1.2.0 for Forge 1.20.1](https://github.com/MCModderAnchor/TACZ/releases/tag/1.2.0-forge-mc1.20.1).

Open Lights previously added a neutral reflectance floor over dark textures and tone-mapped the material-modulated result. Strong surface lighting could flatten texture contrast and bleach colors. Version 2.0.1 separates raw surface irradiance from fog, applies material color at full resolution, and bounds surface light by remaining material brightness. Fog remains additive. Both loader trees use the same shaders.

The integration requires TaCZ `[1.1.8,1.3)` and client Open Lights `[2.0.1,3)`. Install both updated JARs to receive the rendering fix. Neither optional Open Lights addon is required.

## Builds and automated checks

```powershell
.\gradlew.bat :test :build :open-lights:test :open-lights:build -PintegrationQa integrationQaJar
```

The final Forge run used Java 17. The separate NeoForge 1.21.1 build used Java 21 and `gradlew.bat test build` from `open-lights/neoforge/`. Results:

| Suite | Tests | Failures/errors/skips |
| --- | ---: | --- |
| TaCZ integration unit tests | 13 | 0 / 0 / 0 |
| Open Lights Forge | 108 | 0 / 0 / 0 |
| Open Lights NeoForge | 108 | 0 / 0 / 0 |

Each Open Lights suite includes seven GPU tests using the actual GLSL programs and a hidden OpenGL context. `OPENLIGHTS_SHADER_TEST_NATIVES` was set to the matching loader's native-library directory; these checks were executed, not skipped. They cover zero-light identity, dark-surface brightening, black texels, hue, full-resolution texture contrast, bright headroom, colored illumination, independent fog, cached neutral/native-sky composition, and LOD receivers. Ordinary test runs without that variable skip the GPU tests.

Runtime metadata, production Mixin refmaps, QA exclusion, library/addon content separation, package isolation, and Forge reobfuscation gates passed. No QA helper or baseline renderer is included in the distribution archive.

## Packaged Minecraft checks

Disposable Forge 47.4.23 / Java 17 clients loaded the reobfuscated integration, core library, TaCZ, and the isolated integration QA helper. They ran sequentially, hidden, muted, and without mouse capture. OL Global Illumination and Open Lights Flashlight were absent; the classpath contained no development class/resource directories.

The same release integration and core JARs passed against TaCZ 1.2.0 and 1.1.8-hotfix:

- Actual M4A1 + PEQ-15 and Lopro first-person attachment captures, rendered lights, and shadow passes.
- Actual third-person model captures. TaCZ 1.2 also recorded `TACZ2_EXTERNAL_VBO_CAPTURE` with the native static backend enabled; an aim-based fallback alone cannot pass this check.
- K toggling, synchronized colored-profile reload, resource reload, attachment removal, and empty-hand cleanup.
- No optional addon classes/content, world-light probes, GI probes, or aggregate worker activity.

Dedicated-server checks passed fresh startup, `/reload`, save, stop, and restart with each TaCZ version, both with and without the client Open Lights library installed: eight launches total. These establish server-relay loading, not two-client multiplayer rendering.

## Surface comparison

The material fixture fixes the camera, time of day, FOV, and a receiver wall of colored wool, concrete, wood, and stone. Default white-beam, off, and colored-profile captures were compared at two distances. The close wall is at `z=6` with the player at `(0.5, -59, 0.5)` and FOV 65.

The baseline contains the published Open Lights 2.0.0 renderer, with only its declared version changed to 2.0.1 to satisfy the new integration dependency. Every class and shader entry was verified unchanged. This is a QA-only archive, not a compatible release or a distributable fix.

Close captures show visible illumination with less neutral wash over the materials. In clean receiver crops excluding the weapon, HUD, and native laser, green-wool saturation rose from 0.453 to 0.673 and purple-concrete saturation from 0.754 to 0.849. Stone-brick relative neighboring-pixel luminance detail rose from 0.00716 to 0.01244; absolute detail also rose from 0.549 to 0.768. These describe selected inset receiver crops, not universal pass thresholds or a performance benchmark. The corrected beam still brightened stone, green wool, and purple concrete by 2.06×, 1.58×, and 1.39× relative to the fixed light-off frame. All four reviewed clean off crops were byte-identical between baseline and fixed.

The beam did not clip the whole wall in either default-strength capture; the GPU strong-light cases check the headroom bound separately. Native TaCZ laser visuals remain independent of the flashlight toggle.

## Artifacts and evidence

| Runtime JAR | Bytes | SHA-256 |
| --- | ---: | --- |
| `taczopenlights-1.20.1-1.2.0.jar` | 61669 | `9d28b16b3e86261bef64b3d9c0b2ecdb419d403ad2ffaec0b7a679be8b3536cb` |
| `openlights-1.20.1-2.0.1.jar` | 167356 | `eaa1cdba7dc3fec29c08e205727341cd6bbb7ca6d6565b674b3b1ae3bab0441c` |

The `build/distributions/TACZ-Open-Lights-1.2.0-Forge-1.20.1.zip` contains those two JARs, installation notes, and the license. Its contents were checked byte-for-byte against the runtime artifacts.

Ignored evidence routes:

- `.research/tacz12-final-build.log` and `.research/openlights201-neoforge-final.log`.
- `.research/tacz2-1.2.0-fixed-20261107T034612443889.json` and `.research/tacz2-1.2.0-legacy118-20261107T035139758105.json`: TaCZ 1.2 and 1.1.8 client/dedicated matrices.
- `.research/tacz12-final-audit.json`: final test counts, matching runtime/bundle hashes, and passed packaged receipts.
- `.research/tacz2-1.2.0-baseline-close-20261107T034844096612.json` and `.research/tacz2-1.2.0-fixed-close-20261107T034957506450.json`: close comparison receipts.
- `.research/material-comparison-close/`: paired frames, inset material crops, descriptive pixel metrics, and `clean-receiver-regions.json` for the reviewed laser-free crops.
- `.research/material-baseline-QA-ONLY/`: original/baseline hashes and the metadata-only transformation receipt.
- `.research/tacz2-packaged-qa.py`: launcher; per-run directories preserve installed artifact hashes, logs, and screenshots.

Native additive material exposure is an estimate from the camera lightmap. A receiver under different ambient lighting, fog, translucency, or a terrain fade can differ from that estimate. Shader packs, live Distant Horizons, arbitrary gun packs/replacement attachment skins, full modpacks, NeoForge gameplay, and two-client multiplayer were not retested for this change. Earlier compatibility records apply only to their recorded releases.
