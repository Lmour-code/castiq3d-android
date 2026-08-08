package com.castiq.geometry

import kotlin.math.sqrt

/** Minimal 3D vector — deliberately dependency-free so the geometry engine has zero external risk. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-8f) this else Vec3(x / l, y / l, z / l)
    }

    fun distanceTo(o: Vec3) = (this - o).length()

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}
