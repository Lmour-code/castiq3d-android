package com.castiq.render

import android.opengl.Matrix
import com.castiq.geometry.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orbit/arcball camera: rotate around a target point, dolly zoom, pan the target.
 * Y-up convention (matches AngleEngine's native-app coordinate convention).
 */
class OrbitCamera {
    var target = Vec3(0f, 0.5f, 0f)
    var distance = 3f
        set(value) { field = value.coerceIn(0.3f, 50f) }
    var yaw = 0.6f      // radians
    var pitch = 0.4f    // radians, clamped to avoid gimbal flip at the poles

    fun rotate(dYaw: Float, dPitch: Float) {
        yaw += dYaw
        pitch = (pitch + dPitch).coerceIn(-1.5f, 1.5f)
    }

    fun zoom(factor: Float) {
        distance *= factor
    }

    fun pan(dx: Float, dy: Float) {
        val forward = floatArrayOf(
            (cos(pitch) * sin(yaw)).toFloat(), sin(pitch).toFloat(), (cos(pitch) * cos(yaw)).toFloat()
        )
        val worldUp = floatArrayOf(0f, 1f, 0f)
        val right = cross(forward, worldUp)
        val up = cross(right, forward)
        target = target + Vec3(right[0], right[1], right[2]) * (-dx) + Vec3(up[0], up[1], up[2]) * dy
    }

    fun reset() { target = Vec3(0f, 0.5f, 0f); distance = 3f; yaw = 0.6f; pitch = 0.4f }

    fun computeViewMatrix(): FloatArray {
        val eyeX = target.x + distance * cos(pitch) * sin(yaw)
        val eyeY = target.y + distance * sin(pitch)
        val eyeZ = target.z + distance * cos(pitch) * cos(yaw)
        val view = FloatArray(16)
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, target.x, target.y, target.z, 0f, 1f, 0f)
        return view
    }

    /** Ray (origin, normalized direction) from the camera through a normalized device coordinate. */
    fun screenPointToRay(ndcX: Float, ndcY: Float, viewMatrix: FloatArray, projMatrix: FloatArray): Pair<Vec3, Vec3> {
        val vp = FloatArray(16)
        val invVP = FloatArray(16)
        Matrix.multiplyMM(vp, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.invertM(invVP, 0, vp, 0)

        val nearPoint = unproject(invVP, ndcX, ndcY, -1f)
        val farPoint = unproject(invVP, ndcX, ndcY, 1f)
        return nearPoint to (farPoint - nearPoint).normalized()
    }

    private fun unproject(invVP: FloatArray, x: Float, y: Float, z: Float): Vec3 {
        val clip = floatArrayOf(x, y, z, 1f)
        val world = FloatArray(4)
        Matrix.multiplyMV(world, 0, invVP, 0, clip, 0)
        return if (world[3] != 0f) Vec3(world[0] / world[3], world[1] / world[3], world[2] / world[3])
        else Vec3(world[0], world[1], world[2])
    }

    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )
}
