package com.castiq.geometry

/**
 * CPU-side ray/triangle picking against the loaded mesh — no physics engine or third-party
 * collision library required. Uses the standard Moller-Trumbore intersection algorithm.
 */
object RayPicker {

    data class Hit(val point: Vec3, val distance: Float)

    /**
     * Returns the closest intersection of the ray (origin, direction) against the given
     * triangle soup (each triangle = 3 consecutive Vec3 in `triangles`), or null if no hit.
     */
    fun pick(origin: Vec3, direction: Vec3, triangles: List<Vec3>): Hit? {
        var closest: Hit? = null
        var i = 0
        while (i + 2 < triangles.size) {
            val v0 = triangles[i]; val v1 = triangles[i + 1]; val v2 = triangles[i + 2]
            val t = intersectTriangle(origin, direction, v0, v1, v2)
            if (t != null && (closest == null || t < closest.distance)) {
                closest = Hit(origin + direction * t, t)
            }
            i += 3
        }
        return closest
    }

    private const val EPSILON = 1e-6f

    private fun intersectTriangle(origin: Vec3, dir: Vec3, v0: Vec3, v1: Vec3, v2: Vec3): Float? {
        val edge1 = v1 - v0
        val edge2 = v2 - v0
        val h = dir.cross(edge2)
        val a = edge1.dot(h)
        if (a > -EPSILON && a < EPSILON) return null // ray parallel to triangle plane
        val f = 1f / a
        val s = origin - v0
        val u = f * s.dot(h)
        if (u < 0f || u > 1f) return null
        val q = s.cross(edge1)
        val v = f * dir.dot(q)
        if (v < 0f || u + v > 1f) return null
        val t = f * edge2.dot(q)
        return if (t > EPSILON) t else null
    }
}
