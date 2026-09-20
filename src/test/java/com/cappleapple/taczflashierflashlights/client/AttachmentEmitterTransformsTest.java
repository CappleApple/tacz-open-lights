package com.cappleapple.taczflashierflashlights.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentEmitterTransformsTest {
    @Test
    void loproEmitsFromFrontLensAlongWeaponAxis() {
        Matrix4f bone = loproLensBone(new Matrix4f());
        Matrix4f emitter = AttachmentEmitterTransforms.atLens(
                "tacz:laser_lopro", "flashlight_illuminated", bone);

        // Front-center in model space, independently computed from TaCZ's lens
        // cube: pivot (.57525, .55, -1.50639), parent (.5625, .5, -1.5),
        // parent X rotation 90 degrees, and the lens's inflated -Y face.
        assertVector(new Vector3f(0.57525f / 16, 23.50639f / 16, -2.2395f / 16),
                emitter.transformPosition(new Vector3f()));
        assertVector(new Vector3f(0, 0, -1),
                emitter.transformDirection(new Vector3f(0, 0, -1)).normalize());
    }

    @Test
    void loproKeepsGunAnimationAndMountTransform() {
        Matrix4f animatedMount = new Matrix4f().translation(1.2f, -0.6f, -0.8f)
                .rotateZYX(0.4f, -0.35f, 0.2f).scale(1.3f);
        Matrix4f bone = loproLensBone(animatedMount);
        Matrix4f saved = new Matrix4f(bone);
        Matrix4f emitter = AttachmentEmitterTransforms.atLens(
                "tacz:laser_lopro", "flashlight_illuminated", bone);

        Vector3f front = new Vector3f(0.57525f / 16, 23.50639f / 16, -2.2395f / 16);
        assertVector(animatedMount.transformPosition(front), emitter.transformPosition(new Vector3f()));
        assertVector(animatedMount.transformDirection(new Vector3f(0, 0, -1)).normalize(),
                emitter.transformDirection(new Vector3f(0, 0, -1)).normalize());
        assertEquals(saved, bone, "calibration must not mutate TaCZ's pose stack");
    }

    @Test
    void customBonesAreNotGivenDefaultPackCorrections() {
        Matrix4f pose = new Matrix4f().translation(1, 2, 3).rotateY(0.25f);
        assertEquals(pose, AttachmentEmitterTransforms.atLens("custom:laser_lopro", "flashlight_illuminated", pose));
        assertEquals(pose, AttachmentEmitterTransforms.atLens("tacz:laser_lopro", "custom_emitter", pose));
    }

    private static Matrix4f loproLensBone(Matrix4f mount) {
        // Values from TaCZ 1.1.8's laser_lopro_geo.json, using BedrockModel's
        // parent-relative pivot conversion (Y inverted), then BedrockPart's
        // translation / Z-Y-X rotation ordering. Shared by detail and LOD fallback.
        float rootX = -18.01691f, rootY = -1.625f, rootZ = -0.25f;
        return new Matrix4f(mount)
                .translate(rootX / 16, (24 - rootY) / 16, rootZ / 16)
                .translate((0.5625f - rootX) / 16, (rootY - 0.5f) / 16, (-1.5f - rootZ) / 16)
                .rotateX((float) Math.PI / 2)
                .translate((0.57525f - 0.5625f) / 16, (0.5f - 0.55f) / 16,
                        (-1.50639f + 1.5f) / 16);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1.0e-5);
        assertEquals(expected.y, actual.y, 1.0e-5);
        assertEquals(expected.z, actual.z, 1.0e-5);
    }
}
