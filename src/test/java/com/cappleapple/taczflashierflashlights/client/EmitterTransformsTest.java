package com.cappleapple.taczflashierflashlights.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmitterTransformsTest {
    @Test
    void rotatesFirstAndThirdPersonEmittersFromViewToWorld() {
        Matrix4f emitter = new Matrix4f().translation(1, 2, -3);
        Quaternionf camera = new Quaternionf().rotationY((float) Math.PI / 2);
        for (boolean firstPerson : new boolean[]{true, false}) {
            var result = EmitterTransforms.toWorld(emitter, firstPerson, camera, 100, 60, 200);
            assertNotNull(result);
            assertEquals(97, result.x(), 1.0e-5);
            assertEquals(62, result.y(), 1.0e-5);
            assertEquals(199, result.z(), 1.0e-5);
            assertVector(new Vector3f(-1, 0, 0), result.direction());
        }
    }

    @Test
    void preservesSmallOffsetsAtWorldBorderCoordinates() {
        var result = EmitterTransforms.toWorld(new Matrix4f().translation(0.125f, 0.25f, -0.5f),
                true, new Quaternionf(), 29_000_000.125, 256.5, -29_000_000.75);
        assertNotNull(result);
        assertEquals(29_000_000.25, result.x(), 1.0e-8);
        assertEquals(256.75, result.y(), 1.0e-8);
        assertEquals(-29_000_001.25, result.z(), 1.0e-8);
    }

    @Test
    void removesWorldViewRotationIncludingRollFromThirdPersonMatrix() {
        Quaternionf viewRotation = new Quaternionf().rotationZYX(0.4f, 1.1f, -0.3f);
        Quaternionf inverseView = new Quaternionf(viewRotation).invert();
        Matrix4f worldEmitter = new Matrix4f().translation(2, 3, 4).rotateY(0.3f).scale(2, 3, 4);
        Matrix4f cameraEmitter = new Matrix4f().rotate(viewRotation).mul(worldEmitter);
        var result = EmitterTransforms.toWorld(cameraEmitter, false, inverseView, 11, 12, 13);
        assertNotNull(result);
        assertEquals(13, result.x(), 1.0e-5);
        assertEquals(15, result.y(), 1.0e-5);
        assertEquals(17, result.z(), 1.0e-5);
        assertVector(worldEmitter.transformDirection(new Vector3f(0, 0, -1)).normalize(), result.direction());
    }

    @Test
    void doesNotMutateCapturedInputs() {
        Matrix4f matrix = new Matrix4f().translation(1, 2, 3).rotateX(0.7f);
        Matrix4f savedMatrix = new Matrix4f(matrix);
        Quaternionf rotation = new Quaternionf().rotationZ(0.5f);
        Quaternionf savedRotation = new Quaternionf(rotation);
        EmitterTransforms.toWorld(matrix, true, rotation, 0, 0, 0);
        assertEquals(savedMatrix, matrix);
        assertEquals(savedRotation, rotation);
    }

    @Test
    void rejectsCollapsedAndNonFiniteTransforms() {
        assertNull(EmitterTransforms.toWorld(new Matrix4f().scaling(0), true, new Quaternionf(), 0, 0, 0));
        assertNull(EmitterTransforms.toWorld(new Matrix4f().translation(Float.NaN, 0, 0), true,
                new Quaternionf(), 0, 0, 0));
        assertNull(EmitterTransforms.toWorld(new Matrix4f(), true, new Quaternionf(),
                Double.POSITIVE_INFINITY, 0, 0));
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1.0e-5);
        assertEquals(expected.y, actual.y, 1.0e-5);
        assertEquals(expected.z, actual.z, 1.0e-5);
    }
}
