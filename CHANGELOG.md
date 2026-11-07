# Changelog

## 1.2.0 - 2026-11-06

### Added

- Support for TaCZ 1.2.0 on Minecraft 1.20.1. TaCZ 1.1.8 remains supported.

### Fixed

- Flashlight lighting washing out block colors and textures.

### Changed

- Requires Open Lights 2.0.1 or newer.

## 1.1.3 - Unreleased

### Fixed

- Provide a distinct integration build that accepts Open Lights 2.x. Older 1.1.2 binaries restricted Open Lights to versions below 2.0.
- Verify packaged version and dependency metadata, and keep development QA classes and refmaps out of the runtime JAR.

TaCZ weapon lights require only the Open Lights library. OL Global Illumination and Open Lights Flashlight are optional separate mods.

## 1.1.2 - 2026-09-20

### Changed

- License TACZ Open Lights under CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers.
- Build against Open Lights as a pinned Git submodule from its separate repository.

## 1.1.1 - 2026-09-19

### Fixed

- Disable Tactical Breaching's Nightstick flashlight and unbind its toggle on the first client launch, preventing duplicate lighting and a conflicting K binding.
- Keep that key's Controls reset default unbound while preserving later manual configuration and keybind changes.

## 1.1.0 - 2026-09-18

### Added

- Apply synchronized Open Lights beam profiles to TaCZ attachments, with server defaults and datapack overrides.

### Fixed

- Lopro flashlights emitting sideways instead of through the lens.

## 1.0.0 - 2026-09-18

### Added

- Animated first-person and third-person weapon flashlights rendered through Open Lights.
- A weapon flashlight toggle, bound to K by default, with optional server synchronization.
- Attachment emitter mappings and automatic recognition for gun packs.
- Aim-based fallback illumination for hidden or culled player weapon models.
- Oculus shadow-pass handling for attachment pose capture.
