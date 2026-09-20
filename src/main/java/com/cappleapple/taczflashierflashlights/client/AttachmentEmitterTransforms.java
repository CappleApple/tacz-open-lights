package com.cappleapple.taczflashierflashlights.client;

import org.joml.Matrix4f;

/** Lens locations and optical axes in the default TaCZ pack's rendered bone coordinates. */
final class AttachmentEmitterTransforms {
    private AttachmentEmitterTransforms() {}

    static Matrix4f atLens(String attachmentId, String boneName, Matrix4f renderedBone) {
        Matrix4f emitter = new Matrix4f(renderedBone);
        // Default-pack lamp pivots are not always at the front of the lens.
        // Offsets use blocks and follow the bone's animated local axes.
        if (attachmentId.equals("tacz:laser_peq15") && boneName.equals("flashlight")) {
            emitter.translate(-0.023860625f, 0.4596275f, -0.181978125f);
        } else if (attachmentId.equals("tacz:laser_nightstick") && boneName.equals("laser_illuminated")) {
            emitter.translate(0, -0.0703125f, -0.09375f);
        } else if (attachmentId.equals("tacz:laser_lopro") && boneName.equals("flashlight_illuminated")) {
            // TaCZ laser_lopro_geo: parent bone4 rotates X by +90 degrees.
            // The illuminated tube's front is local -Y, 0.6875 model units
            // from its pivot, plus 0.002 units of model inflation. Undo only
            // this fixed mesh orientation; retain the captured gun animation.
            emitter.translate(0, -(0.6875f + 0.002f) / 16.0f, 0)
                    .rotateX(-(float) Math.PI / 2.0f);
        }
        return emitter;
    }
}
