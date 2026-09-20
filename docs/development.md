# Development validation

Both projects target Minecraft 1.20.1, Forge 47.4.10, and Java 17. The development TaCZ dependency is 1.1.8-hotfix.

The root project builds **TACZ Open Lights** (`taczopenlights`). The `open-lights` subproject builds **Open Lights** (`openlights`). The addon keeps its Java package, `com.cappleapple.taczflashierflashlights`.

## Build and automated checks

Run from the repository root:

```powershell
.\gradlew.bat :test :open-lights:test :build :open-lights:build
.\gradlew.bat runServer
```

Release builds verify that each JAR contains its Mixin refmap and excludes the smoke harness. Main-source compilation is non-incremental so partial recompilation cannot omit mappings.

The two JARs are written to `build/libs/` and `open-lights/build/libs/`. The server run checks common-side loading; it does not exercise rendering.

The XML reports from 2026-09-18 record **50 passing tests**, with no failures, errors, or skipped tests:

| Coverage | Tests |
| --- | ---: |
| Weapon and attachment emitter coordinate transforms | 8 |
| Player toggle isolation, tracking, and cleanup | 5 |
| Public light definitions and handle ownership/lifecycle | 10 |
| Triangle winding, exact medium merging, and immutable snapshots | 8 |
| Beam profiles, inheritance, validation, and bounded network codec | 12 |
| Dust sampling, layer colors, cone bounds, and range caps | 7 |

Reports are generated under `build/test-results/test/` and `open-lights/build/test-results/test/`.

## Disposable client test

The `smoke` source set is excluded from release JARs. Create a disposable world at `run/saves/SmokeWorld`, then run:

```powershell
.\gradlew.bat -Psmoke runClient
```

The harness opens that world, replaces the test player's held item, constructs a wall and colored-glass fixture, and changes time and game rules. It mutes audio, releases the mouse, and hides the client window.

The 1.1.0 runs on 2026-09-18 completed all 36 stages with shaders enabled and disabled, each logging `SMOKE_SUCCESS`. Coverage includes:

- Actual first-person attachment model captures, third-person capture, weapon toggles, hidden-HUD fallback, and cleanup.
- Public Point, Spot, and Area handle updates, stained-glass transmission, occluder changes, shadow-slot reassignment, and shadow-target resizing.
- Native handheld and block interactions, dye/range/toggle changes, and water/ice volumes.
- Profile login synchronization, live reload, removed-profile fallback, and active dust particles.
- Lopro lens direction in first and third person, plus paired grazing-angle shadow/control screenshots.

These assertions inspect captured poses, collected frame lights, scene geometry, profile snapshots, particle counts, and executed shadow passes. They establish that the rendering paths ran. Screenshot inspection is a separate check of appearance.

Each timing run collects 180 frames after a two-second warmup, with three point lights, three spot lights, two area lights, and a four-light shadow limit. Stationary and moving workloads are reported separately.

Success appears as `SMOKE_SUCCESS` in `run/logs/latest.log`; assertions and timeouts produce `SMOKE_FAILURE`. Screenshots are saved to `run/screenshots/smoke-<stage-number>-<stage-name>.png`. Timing output uses `SMOKE_PERFORMANCE` and `SMOKE_PERFORMANCE_CONTEXT`, including CPU/GPU mean and p95, shadow passes, GPU identity, resolution, and render settings. These are Open Lights renderer timings, not whole-game frame times or comparisons with another mod.

## Renderer-mod test

The optional `shaderSmoke` property adds the pinned Embeddium and Oculus development dependencies:

```powershell
.\gradlew.bat -Psmoke -PshaderSmoke runClient
```

Both properties are needed to run the automated harness with those mods. The flag does not install or enable a shader pack. Record the exact enabled pack and its settings when validating shader-pack rendering; an Oculus run without an enabled pack only checks that mod combination. Runtime compatibility and performance results are separate from the baseline above.

Tested on 2026-09-18 with Embeddium 0.3.31, Oculus 1.8.0, and Complementary Reimagined r5.9.3. The shader-enabled log confirms that Oculus loaded the pack. Screenshot inspection confirmed the weapon beam, profile color changes, and colored-glass transmission. The brighter shader-disabled grazing fixture showed a continuous illuminated floor without self-shadow stripes, while its central block retained a visible shadow; the paired unshadowed control removed that shadow. The pack logged nonfatal missing-uniform warnings for features from newer Minecraft versions.

At 960x540 on an NVIDIA RTX 5070 Ti, default Open Lights quality and one transparent region. The shader-enabled fixture contained 1,340 scene triangles; the shader-disabled run retained the larger grazing-test floor and contained 3,140:

| Eight-light workload | CPU mean / p95 | GPU mean / p95 |
| --- | --- | --- |
| Stationary, shaders on | 0.0796 / 0.1975 ms | 0.4356 / 0.4554 ms |
| Moving, shaders on | 0.1280 / 0.2430 ms | 0.5373 / 0.5599 ms |
| Stationary, shaders off | 0.0910 / 0.2370 ms | 0.2381 / 0.2409 ms |
| Moving, shaders off | 0.1405 / 0.2858 ms | 0.2808 / 0.2837 ms |

These are measurements of the small test fixture on this GPU. GPU timing covers shadow and lighting composition, excluding the earlier opaque-depth copy. CPU timing includes scene-cache work in the composition stage, excluding providers and the earlier depth capture. Neither timing includes the normal Minecraft particle pass. They do not establish performance on dense worlds or other GPUs.

Both 1.1.0 mods passed a fresh dedicated-server startup with TaCZ, generated the server configuration, completed a datapack reload, and saved all dimensions on an orderly stop.

## Tactical Breaching defaults check

TACZ Open Lights 1.1.1 adds a one-time client settings migration for Tactical Breaching 1.0.5. Open Lights remains at 1.1.0. The optional addon is a development dependency only when explicitly requested:

```powershell
.\gradlew.bat -Psmoke -PtacticalBreachingSmoke=migrate runClient
.\gradlew.bat -Psmoke -PtacticalBreachingSmoke=preserve runClient
.\gradlew.bat -Psmoke -PtacticalBreachingSmoke=absent runClient
```

Use the disposable `run` directory. Before the first command, set `tacticalBreachingDefaultsApplied = false` in its `config/taczopenlights-client.toml` if the migration has already run. The first launch checks disabled flashlight emission, an unbound current/default key, the unchanged Open Lights K binding, and Tactical Breaching's unchanged J binding. It then saves a deliberate enabled/L override. The second launch checks that override survives, then restores disabled/unbound settings. The third launch omits Tactical Breaching and checks normal client startup. Each successful run logs `COMPAT_SMOKE_SUCCESS`; these checks exit at the title screen and do not claim visual rendering validation.

All three gates passed on 2026-09-19; the migration and preservation gates used Tactical Breaching 1.0.5. Their clients ran hidden and muted. The 1.1.1 addon also passed the 50-test build and dedicated-server startup/shutdown gate without the optional addon. The migration uses Forge's loaded configuration and registered key mappings; it does not copy or bundle Tactical Breaching code.

## Renderer and cache

Open Lights captures opaque scene depth and view/projection matrices during Forge world-render events, then composites its light buffer at `AFTER_LEVEL`, before first-person hands. The renderer and shaders are implemented in this repository.

Opaque block voxel shapes are cached in eight-block sectors around the camera, within a 32-block radius. The cache scans at most 8,192 primary block positions per game tick; face visibility checks also read neighboring blocks. Calls within the same tick reuse the snapshot. Initial coverage fills incrementally, and unchanged geometry is not uploaded again.

Full opaque neighbors suppress interior cube faces. Block-change notifications prioritize affected sectors for rebuilding. The Open Lights client setting `mediumUpdateTicks`, default `10`, controls when a cached sector becomes eligible for periodic refresh. The scan budget still applies, so a complete region sweep can take longer than that interval.

Transparent media use individual shape boxes and actual water height. Exact-axis merging preserves pane gaps and different fluid heights. Waterlogged solids are subtracted from water volumes. Tinted glass remains an opaque shadow caster; light-source blocks are excluded to prevent self-shadowing.

Stationary sources reuse shadow maps until their definition or scene revision changes. Point, spot, and area lights use six, one, and four shadow views respectively. Lighting uses a reduced-resolution buffer, bounded volumetric samples, depth-aware upsampling, and asynchronous GPU timing. Receiver-plane shadow comparisons account for each sampled shadow texel to prevent grazing-angle self-shadow stripes. Inner and outer beam layers share one spot-light shadow view; transparent media use a uniform buffer to stay within OpenGL 3.3 fragment-uniform limits.

Current defaults are eight rendered lights, four shadowed lights, a 24-block light range, 256-pixel shadow faces, 12 volumetric samples, and a `0.5` render scale. The client configs are `config/openlights-client.toml` and `config/taczopenlights-client.toml`.

Shadow casters currently consist of block voxel shapes. Animated entity and block-entity meshes are outside the cached geometry. Shader-pack composition order and appearance require runtime validation for the specific renderer and pack. Open Lights does not feed a shader pack's internal exposure, material lighting, TAA, or bloom pipeline. Two-client multiplayer also requires separate validation.

`OpenLightRenderer.statistics()` exposes internal diagnostics. Integrations should use the [public client API](https://github.com/CappleApple/open-lights/blob/main/docs/api.md).

## Source provenance

Optical material constants and transparent-volume behavior are adapted from the MIT-licensed Veil Volume Lights project, with attribution retained in source. Open Lights' renderer and shaders are independently implemented. TaCZ is consumed as a separate dependency through its public types and model-render hooks.
