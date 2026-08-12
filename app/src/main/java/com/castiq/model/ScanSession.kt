package com.castiq.model

import com.castiq.geometry.Vec3
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Landmark(val id: String, val name: String, val position: Vec3)

data class AngleMeasurement(
    val axisALabel: String,
    val axisBLabel: String,
    val rawAngleDeg: Float,
    val plane: String?,          // null when "Raw 3D (no projection)" was used
    val planeAngleDeg: Float?,
    val target: Float,
    val tolerance: Float,
    val deviation: Float,
    val status: String
)

data class DistanceMeasurement(
    val fromLabel: String,
    val toLabel: String,
    val modelDistance: Float,
    val mm: Float?               // null if not calibrated — never guessed
)

/**
 * Stage 1 in-memory scan record. Its shape intentionally mirrors the long-term Structural Twin
 * scan object from the engineering brief (scan_id, device_id, capture_mode, calibration,
 * landmarks, measurements, confidence, source data) so later stages can extend it rather than
 * replace it. Fields for point_cloud / mesh_file / trimline / confidence are explicit JSON
 * nulls, not fabricated values — those are Stage 5+ work.
 */
class ScanSession(val scanId: String = UUID.randomUUID().toString()) {
    var deviceId: String = ""
    val captureMode: String = "manual_landmark_v1" // PLANNED: "depth_ar" / "photogrammetry" later
    val landmarks = ArrayList<Landmark>()
    val angleMeasurements = ArrayList<AngleMeasurement>()
    val distanceMeasurements = ArrayList<DistanceMeasurement>()
    var calibrationScale: Float? = null       // model units -> mm
    var calibrationReferenceMm: Float? = null

    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("scan_id", scanId)
        root.put("device_id", deviceId)
        root.put("capture_mode", captureMode)
        root.put("coordinate_system", JSONObject().apply {
            put("x", "medial_lateral"); put("y", "vertical"); put("z", "anterior_posterior")
        })
        root.put("calibration", JSONObject().apply {
            put("scale_mm_per_unit", calibrationScale ?: JSONObject.NULL)
            put("reference_distance_mm", calibrationReferenceMm ?: JSONObject.NULL)
        })
        root.put("landmarks", JSONArray().apply {
            landmarks.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("name", it.name)
                    put("x", it.position.x); put("y", it.position.y); put("z", it.position.z)
                })
            }
        })
        root.put("angle_measurements", JSONArray().apply {
            angleMeasurements.forEach {
                put(JSONObject().apply {
                    put("axis_a", it.axisALabel); put("axis_b", it.axisBLabel)
                    put("raw_angle_deg", it.rawAngleDeg)
                    put("plane", it.plane ?: JSONObject.NULL)
                    put("plane_angle_deg", it.planeAngleDeg ?: JSONObject.NULL)
                    put("target_deg", it.target); put("tolerance_deg", it.tolerance)
                    put("deviation_deg", it.deviation); put("status", it.status)
                })
            }
        })
        root.put("distance_measurements", JSONArray().apply {
            distanceMeasurements.forEach {
                put(JSONObject().apply {
                    put("from", it.fromLabel); put("to", it.toLabel)
                    put("model_distance", it.modelDistance)
                    put("mm", it.mm ?: JSONObject.NULL)
                })
            }
        })
        // Explicit placeholders for capabilities not yet built — never faked with sample values.
        root.put("point_cloud", JSONObject.NULL)
        root.put("mesh_file", JSONObject.NULL)
        root.put("trimline", JSONObject.NULL)
        root.put("confidence", JSONObject.NULL)
        return root
    }
}
