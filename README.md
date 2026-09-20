# TACZ Open Lights

Forge 1.20.1 weapon flashlight integration for TaCZ, powered by the independently implemented [Open Lights](https://github.com/CappleApple/open-lights) mod.

Open Lights is a standalone mod with point, spot, and rectangular area lights, block-shape shadows, volumetric beams, colored glass transmission, a handheld flashlight, and placeable lights. Other mods can use its [public API](https://github.com/CappleApple/open-lights/blob/main/docs/api.md).

## Installation

Use Minecraft 1.20.1, Java 17, and Forge 47.4.10 or newer in the 47.x series.

Install:

1. [TaCZ 1.1.8-hotfix](https://modrinth.com/mod/timeless-and-classics-zero).
2. `openlights-1.20.1-1.1.1.jar`.
3. `taczopenlights-1.20.1-1.1.2.jar`.

Install Open Lights on both clients and the server for its items and blocks. Install the TaCZ addon on the server too to synchronize weapon-light toggles. Without the addon server relay, your toggle works locally and other players' recognized weapon lights default to enabled.

Neither mod depends on Flashier Flashlights or Veil.

Embeddium 0.3.31 and Oculus 1.8.0 were tested with Complementary Reimagined r5.9.3. Install those optional mods normally; Open Lights detects the shader API automatically. See [rendering limits](https://github.com/CappleApple/open-lights#rendering-and-limits) for the tested scope.

## Weapon lights

Install a flashlight attachment on a compatible TaCZ gun and hold it in your main hand. Press **K** to toggle the weapon light. Rebind **Toggle Weapon Flashlight** in Controls.

The toggle belongs to the player for the current connection; switching guns preserves it. TaCZ attachments do not consume batteries.
When TaCZ Tactical Breaching is installed, the first client launch with this addon disables its Nightstick flashlight and unbinds **Toggle Nightstick Flashlight**. This prevents its default K binding from competing with Open Lights. Later manual changes to that setting or key are preserved; Controls' Reset action uses an unbound default while this addon is installed. Other Tactical Breaching controls are unchanged.

The one-time change sets `nightstick_flashlight.enableNightstickFlashlight = false` in `config/tacz_tactical_breaching-common.toml` and saves the unbound key in `options.txt`. The addon records completion with `tacticalBreachingDefaultsApplied` in `config/taczopenlights-client.toml`; set it to `false` and restart to reapply those defaults. These saved choices remain if the addon is removed. This integration targets Tactical Breaching's flashlight, not a built-in TaCZ 1.1.8 flashlight toggle.

Bundled PEQ-15, PEQ-6, Nightstick, and Lopro lights are recognized. The compact laser is excluded by default. Visible beams follow the animated attachment. Simplified third-person models use the detailed model's emitter path when needed. Hidden or off-screen player weapons use an approximate pose based on their aim.

First-person animation uses the preceding frame's attachment pose with the current camera because world lighting runs before TaCZ draws the hands.

## Beam appearance

Set server defaults in the world's `serverconfig/openlights-server.toml`. Datapacks can override individual attachments, with separate inner/outer color, brightness, angle, range, edge softness, and falloff, plus fog and dust. Settings synchronize on login and `/reload`.

See the [beam profile guide](https://github.com/CappleApple/open-lights/blob/main/docs/beam-profiles.md) and [example datapack](https://github.com/CappleApple/open-lights/tree/main/examples/beam-profiles).

## Configuration

The addon creates `config/taczopenlights-client.toml`.

| Key | Default | Behavior |
| --- | --- | --- |
| `enabled` | `true` | Render weapon lights. |
| `automaticDetection` | `true` | Recognize flashlight-like bones on LASER attachments. |
| `remoteDistance` | `64.0` | Provider cutoff for remote weapon lights; Open Lights also applies its 32-block scene limit. |
| `emitters` | Mappings below | Assign attachment IDs to exact model bones. |
| `disabledAttachments` | `["tacz:laser_compact"]` | Exclude attachments. |

```toml
emitters = [
    "tacz:laser_peq15=flashlight",
    "tacz:laser_peq6=flash_illuminated",
    "tacz:laser_nightstick=laser_illuminated"
]
```

Automatic detection accepts `flashlight`, `flash`, `weapon_light`, `light`, and `emitter`, optionally followed by `_illuminated` and a numeric suffix. Automatic matching ignores case; explicit bone names match case exactly. A `laser_beam` bone alone does not qualify.

Open Lights controls render budgets and quality in `config/openlights-client.toml`. See its [settings and rendering limits](https://github.com/CappleApple/open-lights#configuration).

## Building

Open Lights is pinned as the `open-lights` Git submodule. Clone with `git clone --recurse-submodules https://github.com/CappleApple/tacz-open-lights.git`, or run `git submodule update --init --recursive` in an existing checkout. Build with Java 17:

```powershell
.\gradlew.bat test build
```

Outputs:

- `build/libs/taczopenlights-1.20.1-1.1.2.jar`
- `open-lights/build/libs/openlights-1.20.1-1.1.1.jar`

Development dependencies are downloaded by Gradle and are not bundled in either mod. The addon retains the requested Java package `com.cappleapple.taczflashierflashlights`; Open Lights uses `com.cappleapple.openlights`.

See [development validation](docs/development.md) and the [Open Lights API](https://github.com/CappleApple/open-lights/blob/main/docs/api.md).

## License

[CC BY-NC-SA 4.0 with Additional Permission for Minecraft Modpacks and Servers](LICENSE), copyright 2026 CappleApple. See [third-party notices](THIRD_PARTY_NOTICES.md) for exceptions.
