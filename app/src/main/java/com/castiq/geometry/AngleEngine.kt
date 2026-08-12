package com.castiq.geometry

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.round

/**
 * Real vector-based 3D geometry math for CastIQ.
 * No angle here is ever derived from screen-space / 2D coordinates.
 *
 * COORDINATE CONVENTION (Stage 1, native app):
 *   X = medial <-> lateral
 *   Y = vertical (up)          <-- this is the OpenGL/rendering "up" axis
 *   Z = anterior <-> posterior
 *
 * NOTE — cross-app consistency (see directive section "IMPORTANT — SIGN CONVENTIONS"):
 * The CastIQ *web* prototype (castiq3d.html) used a Z-up convention (X=ML, Y=AP, Z=vertical)
 * to match the Three.js scene it was built in. This native app uses Y-up to match standard
 * OpenGL/Android 3D convention and typical OBJ authoring tools. These are NOT the same axis
 * mapping. Unifying the sign/axis convention across CastIQ web, CastIQ native, LayupIQ and
 * Structural Twin is real outstanding work — do not assume they agree without checking.
 */
object AngleEngine {

    /** True 3D angle (degrees) between two direction vectors: acos(dot(A,B) / (|A||B|)). */
    fun angleBetween3D(a: Vec3, b: Vec3): Float {
        val la = a.length(); val lb = b.length()
        if (la < 1e-8f || lb < 1e-8f) return 0f
        val cos = (a.dot(b) / (la * lb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos.toDouble())).toFloat()
    }

    enum class Plane { SAGITTAL, FRONTAL, TRANSVERSE }

    /**
     * Signed angle (degrees, -180..180) between two vectors after projecting onto an
     * anatomical plane. Assumes the scan has already been oriented so the model's Y axis
     * is genuinely vertical — CastIQ native does not yet have an interactive orientation
     * tool (that is Stage 2/3 work); for Stage 1 the sample test model is authored
     * pre-aligned to this convention.
     */
    fun signedPlaneAngle(a: Vec3, b: Vec3, plane: Plane): Float {
        val (pa1, pa2) = projectToPlane(a, plane)
        val (pb1, pb2) = projectToPlane(b, plane)
        val dot = pa1 * pb1 + pa2 * pb2
        val cross = pa1 * pb2 - pa2 * pb1
        return Math.toDegrees(atan2(cross.toDouble(), dot.toDouble())).toFloat()
    }

    private fun projectToPlane(v: Vec3, plane: Plane): Pair<Float, Float> = when (plane) {
        Plane.SAGITTAL -> v.z to v.y     // anterior-posterior vs vertical
        Plane.FRONTAL -> v.x to v.y      // medial-lateral vs vertical
        Plane.TRANSVERSE -> v.x to v.z   // medial-lateral vs anterior-posterior
    }

    /** Manual landmark placement does not support finer than ~0.5 degree — never display false precision. */
    fun roundHalfDegree(deg: Float): Float = round(deg * 2f) / 2f

    /** Round to 1 decimal place for millimetre display. */
    fun round1(mm: Float): Float = round(mm * 10f) / 10f
}
