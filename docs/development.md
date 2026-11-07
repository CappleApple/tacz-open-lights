# Development validation

TACZ Open Lights targets Minecraft 1.20.1, Forge 47.4.10 or later in 47.x, Java 17, and TaCZ 1.1.8-hotfix or 1.2.0. The integration uses the public Open Lights client API and synchronized beam profiles. Version 1.2.0 requires the Open Lights 2.0.1 library or later in 2.x; neither optional addon is required.

## Build

From the repository root:

```powershell
.\gradlew.bat :test :build
```

The integration JAR is `build/libs/taczopenlights-1.20.1-1.2.0.jar`. The pinned `open-lights` subproject supplies the compile dependency. Neither TaCZ nor Open Lights is bundled in the integration.

The default compile dependency is TaCZ 1.2.0. Use `-Ptacz_version=yOVIzIJR` to compile against TaCZ 1.1.8-hotfix. Both versions must pass packaged client checks before claiming compatibility.

`verifyModJar` checks the expanded integration version, the Open Lights dependency range, the production Mixin refmap, and exclusion of development QA code and refmaps. Main-source compilation is non-incremental so partial compilation cannot omit Mixin mappings.

## Renderer regression checks

Open Lights has seven GPU tests that compile the actual GLSL shaders and draw into a hidden OpenGL context. Set `OPENLIGHTS_SHADER_TEST_NATIVES` to a directory containing the matching LWJGL native libraries, then run:

```powershell
.\gradlew.bat :open-lights:test
```

Without that variable, these GPU checks are skipped. Ordinary unit tests still run. The test task tracks the variable so enabling the checks invalidates an earlier result. Forge and NeoForge need their own matching native libraries.

## Packaged integration checks

Compile the dedicated helper with:

```powershell
.\gradlew.bat -PintegrationQa integrationQaJar
```

`build/libs/taczopenlights-integration-qa.jar` is a development helper, not a runtime dependency. Its source set compiles against the integration, TaCZ, and the Open Lights library only. It does not depend on either optional Open Lights addon.

Packaged checks must use a disposable directory with the integration, TaCZ, the Open Lights 2.0.1 library, and the helper. Keep clients hidden, muted, and without mouse capture. Do not use an existing player world. Run visual clients sequentially.

The focused fixture checks actual TaCZ attachment capture and rendered weapon lights, first- and third-person poses, toggling, profile reload, cleanup, and absence of GI processing without the GI addon. On TaCZ 1.2, a supported native VBO backend must also initialize the external-pose bridge; the fixture records that result explicitly. Launch with `-Dtaczopenlights.materialQa=true` for a fixed-camera wall of colored wool, concrete, wood, and stone receivers. Record completed checks and artifact hashes separately; compiling the helper alone does not establish runtime success.

Do not run the parent and standalone Forge Open Lights builds simultaneously: they share build outputs. Use the reobfuscated library JAR for packaged compatibility checks.

See [1.2.0 verification](verification-1.2.0.md) for current local checks, exact artifact hashes, and coverage limits. [1.1.3 verification](verification-1.1.3.md) records historical checks against that version.

## Earlier checks

[Historical 1.1.0/1.1.1 validation](verification-1.1.0-1.1.1.md) preserves the earlier full rendering and Tactical Breaching checks. Those results apply to their recorded versions. The legacy `smoke` source set imports the former bundled content and needs addon classpaths and fixture updates before reuse with Open Lights 2.0.
