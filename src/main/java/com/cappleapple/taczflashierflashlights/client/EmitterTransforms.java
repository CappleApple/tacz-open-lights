package com.cappleapple.taczflashierflashlights.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts TaCZ's rendered model coordinates into world coordinates. */
public final class EmitterTransforms {
    private EmitterTransforms() {}

    /**
     * Both Forge 1.20.1 world models and first-person hands contain camera-space
     * coordinates. The rotation must be the inverse view rotation, including
     * Forge's camera roll, rather than only the camera's yaw/pitch quaternion.
     * Returns null for a collapsed or non-finite emitter transform.
     */
    public static Transform toWorld(Matrix4f renderedTransform, boolean firstPerson,
                                    Quaternionf viewToWorldRotation,
                                    double cameraX, double cameraY, double cameraZ) {
        Vector3f relativePosition = renderedTransform.transformPosition(new Vector3f());
        Vector3f direction = renderedTransform.transformDirection(new Vector3f(0, 0, -1));
        if (!finite(relativePosition) || !finite(direction) || direction.lengthSquared() < 1.0e-10f) {
            return null;
        }
        viewToWorldRotation.transform(relativePosition);
        viewToWorldRotation.transform(direction);
        if (!finite(relativePosition) || !finite(direction) || direction.lengthSquared() < 1.0e-10f) {
            return null;
        }
        direction.normalize();
        double x = cameraX + relativePosition.x;
        double y = cameraY + relativePosition.y;
        double z = cameraZ + relativePosition.z;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        return new Transform(direction, x, y, z);
    }

    private static boolean finite(Vector3f vector) {
        return Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z);
    }

    public record Transform(Vector3f direction, double x, double y, double z) {}
}
