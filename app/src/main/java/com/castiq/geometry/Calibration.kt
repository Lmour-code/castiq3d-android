package com.castiq.geometry

/**
 * Converts model-space distances to millimetres.
 * The system must never assume 1 model unit = 1 mm — this must be explicitly established
 * against a known real-world reference distance before any measurement is trusted.
 */
class Calibration {
    var modelUnitsToMm: Float? = null
        private set
    var referenceDistanceMm: Float? = null
        private set

    fun set(pointA: Vec3, pointB: Vec3, knownDistanceMm: Float) {
        val modelDist = pointA.distanceTo(pointB)
        if (modelDist > 1e-8f && knownDistanceMm > 0f) {
            modelUnitsToMm = knownDistanceMm / modelDist
            referenceDistanceMm = knownDistanceMm
        }
    }

    fun isSet(): Boolean = modelUnitsToMm != null

    /** Clears calibration when a new mesh is loaded — the old scale is meaningless for a
     *  different scan at a different real-world size. */
    fun reset() { modelUnitsToMm = null; referenceDistanceMm = null }

    /** Returns null (not a guessed value) if calibration has not been set. */
    fun toMm(modelDistance: Float): Float? = modelUnitsToMm?.let { it * modelDistance }
}
