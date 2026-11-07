# TACZ Open Lights 1.1.3 verification

Local compatibility build for Minecraft 1.20.1 / Forge, checked 2026-11-02 against the published Open Lights 2.0.0 library and TaCZ 1.1.8-hotfix.

## Fix

The earlier integration binary restricted Open Lights to versions below 2.0. The source dependency range had already been expanded, but the integration still identified as 1.1.2. Version 1.1.3 provides a distinct replacement JAR with the accepted range `[1.1.0,3)`. No Open Lights API adapter was needed: TaCZ continues to submit frame-local spot lights and read shared beam profiles from the library.

The build checks the packaged version and dependency range and excludes development QA classes and refmaps. Only `taczflashierflashlights.refmap.json` belongs in the runtime JAR.

## Build and unit tests

```powershell
.\gradlew.bat --offline :test :build -PintegrationQa integrationQaJar
```

Thirteen unit tests passed with no failures, errors, or skips. The runtime JAR passed `verifyModJar`. The new `integrationQa` helper compiles against the integration, TaCZ, and the core library only.

## Packaged client

The disposable Forge 47.4.23 / Java 17 client loaded:

- TACZ Open Lights 1.1.3.
- Published `openlights-1.20.1-2.0.0.jar`, SHA-256 `850e354336197962450fce6fcb42db2e542936e3a3eaf4079f36bd6188e91f21`.
- TaCZ 1.1.8-hotfix.
- The isolated integration QA helper.

OL Global Illumination and Open Lights Flashlight were absent. No development class/resource output was placed on the packaged runtime classpath. The client ran hidden, muted, and without mouse capture in a new disposable world.

Passed checks:

- No unsolicited lights while empty-handed, and no GI/world-light cache work.
- Actual first-person M4A1 + PEQ-15 model-pose capture, rendered weapon light, and executed shadow pass.
- Actual K keybinding disables and restores the weapon light.
- Lopro attachment capture and beam direction, including third-person capture.
- Datapack profile reload changes the synchronized Lopro profile and rendered beam to green.
- Resource reload restores active rendering; removing the attachment and emptying the hand remove the light.

The recorded `POSES` checks require a real model capture, preventing an aim-based fallback alone from passing the capture check. PEQ on/off and green profile captures were inspected separately from runtime assertions. Native TaCZ laser visuals are separate from the Open Lights flashlight toggle.

## Dedicated servers

Four dedicated launches passed: fresh startup and restart with the Open Lights library installed, then fresh startup and restart with the library absent. Every launch completed `/reload`, saved, and stopped cleanly. This confirms that the integration’s server relay does not require the client rendering library. No rendering claim is made from server startup.

## Artifact and evidence

Runtime: `taczopenlights-1.20.1-1.1.3.jar`, 56115 bytes, SHA-256 `9f139468f29cab49da6a447f4d8aef62ed2b942c4bafd09468f09c4d67d4f53c`.

Raw build log: ignored `.research/tacz2-build-1.log`. Runtime launcher: ignored `.research/tacz2-packaged-qa.py`. Per-run directories and receipts use the `tacz2-1.1.3-20261102T220812129646` prefix and retain exact installed artifact hashes. Summary: `.research/tacz2-1.1.3-20261102T220812129646.json`. All cases passed, the runtime JAR still matches every installed copy, and the launched processes exited.

This establishes compatibility in the recorded packaged fixture. It does not establish arbitrary gun-pack, shader-pack, modpack, or two-client multiplayer behavior. No user world or installed modpack was modified. The tested JAR was uploaded through the requested CurseForge uploader skill to project `1705183`, accepted as file `9411668`. Moderation and public download availability were not verified. Upload plan and receipt are retained under ignored `.research/curseforge-1705183-1.1.3/`. No GitHub release was made for this integration build.
