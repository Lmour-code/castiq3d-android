package com.castiq.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.castiq.app.R
import com.castiq.geometry.AngleEngine
import com.castiq.geometry.Calibration
import com.castiq.geometry.RayPicker
import com.castiq.geometry.Vec3
import com.castiq.model.AngleMeasurement
import com.castiq.model.DistanceMeasurement
import com.castiq.model.Landmark
import com.castiq.model.ScanSession
import com.castiq.render.CastGLRenderer
import com.castiq.render.CastGLSurfaceView
import java.io.File
import java.io.FileWriter
import java.util.UUID
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var glView: CastGLSurfaceView
    private lateinit var renderer: CastGLRenderer
    private lateinit var txtScanStatus: TextView

    private val pickObjLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val stream = contentResolver.openInputStream(uri) ?: run {
                toast("Could not open that file."); return@registerForActivityResult
            }
            glView.queueEvent {
                stream.use { renderer.loadMesh(it) }
                runOnUiThread { onNewMeshLoaded(uri.lastPathSegment ?: "scan") }
            }
        } catch (e: Exception) {
            toast("Load failed: ${e.message}")
        }
    }

    private val landmarkTypes = listOf(
        "Heel Centre", "Medial Malleolus", "Lateral Malleolus", "Ankle Centre",
        "Tibial Proximal", "Tibial Distal", "Forefoot Centre",
        "First Metatarsal", "Fifth Metatarsal", "Custom Point"
    )
    private val planes = listOf("Sagittal", "Frontal", "Transverse", "Raw 3D (no projection)")

    private val session = ScanSession()
    private val calibration = Calibration()

    private enum class Tool { NONE, CALIBRATE, LANDMARK }
    private var activeTool = Tool.NONE
    private val calibrationTapPoints = ArrayList<Vec3>()

    private lateinit var txtCalStatus: TextView
    private lateinit var txtLandmarks: TextView
    private lateinit var txtResults: TextView
    private lateinit var spinnerLandmarkType: Spinner
    private lateinit var spinnerAxisA1: Spinner
    private lateinit var spinnerAxisA2: Spinner
    private lateinit var spinnerAxisB1: Spinner
    private lateinit var spinnerAxisB2: Spinner
    private lateinit var spinnerDistFrom: Spinner
    private lateinit var spinnerDistTo: Spinner
    private lateinit var spinnerPlane: Spinner
    private lateinit var editCalMm: EditText
    private lateinit var editTarget: EditText
    private lateinit var editTolerance: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        session.deviceId = UUID.randomUUID().toString()

        glView = findViewById(R.id.glView)
        renderer = CastGLRenderer(this, "models/sample_afo_cast.obj")
        glView.initRenderer(renderer)

        txtCalStatus = findViewById(R.id.txtCalStatus)
        txtScanStatus = findViewById(R.id.txtScanStatus)
        txtLandmarks = findViewById(R.id.txtLandmarks)
        txtResults = findViewById(R.id.txtResults)
        spinnerLandmarkType = findViewById(R.id.spinnerLandmarkType)
        spinnerAxisA1 = findViewById(R.id.spinnerAxisA1)
        spinnerAxisA2 = findViewById(R.id.spinnerAxisA2)
        spinnerAxisB1 = findViewById(R.id.spinnerAxisB1)
        spinnerAxisB2 = findViewById(R.id.spinnerAxisB2)
        spinnerDistFrom = findViewById(R.id.spinnerDistFrom)
        spinnerDistTo = findViewById(R.id.spinnerDistTo)
        spinnerPlane = findViewById(R.id.spinnerPlane)
        editCalMm = findViewById(R.id.editCalMm)
        editTarget = findViewById(R.id.editTarget)
        editTolerance = findViewById(R.id.editTolerance)

        spinnerLandmarkType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, landmarkTypes)
        spinnerPlane.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, planes)
        refreshLandmarkSpinners()

        findViewById<Button>(R.id.btnLoadScan).setOnClickListener {
            pickObjLauncher.launch(arrayOf("*/*"))
        }
        findViewById<Button>(R.id.btnCalibrateMode).setOnClickListener {
            setActiveTool(Tool.CALIBRATE)
            calibrationTapPoints.clear()
            updateOverlay()
            toast("Calibration: press and drag to position, release to place. 2 points needed.")
        }
        findViewById<Button>(R.id.btnSetCal).setOnClickListener { applyCalibration() }
        findViewById<Button>(R.id.btnPlaceLandmark).setOnClickListener {
            setActiveTool(Tool.LANDMARK)
            toast("Press and drag to position ${landmarkTypes[spinnerLandmarkType.selectedItemPosition]}, release to place.")
        }
        findViewById<Button>(R.id.btnComputeAngle).setOnClickListener { computeAngle() }
        findViewById<Button>(R.id.btnComputeDistance).setOnClickListener { computeDistance() }
        findViewById<Button>(R.id.btnSaveScan).setOnClickListener { saveScan() }
        findViewById<Button>(R.id.btnResetView).setOnClickListener { renderer.camera.reset() }

        glView.onTapPick = { (origin, dir) -> handlePick(origin, dir) }
        glView.onPlacementPreview = { (origin, dir) ->
            val mesh = renderer.currentMesh()
            val hit = mesh?.let { RayPicker.pick(origin, dir, it.triangles) }
            renderer.setPreviewPoint(hit?.point)
        }
        glView.onPlacementCancel = { renderer.setPreviewPoint(null) }
    }

    /** Keeps the tool state and the surface view's touch-interaction mode in sync — placement
     *  mode (offset crosshair drag-to-place) only makes sense while a tool is actually active. */
    private fun setActiveTool(tool: Tool) {
        activeTool = tool
        glView.placementModeActive = (tool != Tool.NONE)
        if (tool == Tool.NONE) renderer.setPreviewPoint(null)
    }

    private fun handlePick(origin: Vec3, dir: Vec3) {
        val mesh = renderer.currentMesh() ?: return
        val hit = RayPicker.pick(origin, dir, mesh.triangles)
        if (hit == null) {
            runOnUiThread { toast("No surface hit — drag over the model before releasing.") }
            return
        }
        runOnUiThread {
            when (activeTool) {
                Tool.CALIBRATE -> {
                    calibrationTapPoints.add(hit.point)
                    renderer.setPreviewPoint(null)
                    updateOverlay()
                    if (calibrationTapPoints.size >= 2) {
                        toast("2 points placed — enter the known distance in mm and tap Set.")
                        setActiveTool(Tool.NONE)
                    }
                }
                Tool.LANDMARK -> {
                    val name = landmarkTypes[spinnerLandmarkType.selectedItemPosition]
                    session.landmarks.add(Landmark(UUID.randomUUID().toString(), name, hit.point))
                    renderer.setPreviewPoint(null)
                    refreshLandmarkSpinners()
                    updateOverlay()
                    setActiveTool(Tool.NONE)
                    toast("$name placed.")
                }
                Tool.NONE -> { /* placement mode inactive, ignore */ }
            }
        }
    }

    private fun onNewMeshLoaded(name: String) {
        // A new mesh means old landmarks, calibration, and measurements are meaningless —
        // they were defined against the previous scan's geometry. Reset the session cleanly
        // rather than silently carrying over numbers that no longer correspond to anything.
        session.landmarks.clear()
        session.angleMeasurements.clear()
        session.distanceMeasurements.clear()
        session.calibrationScale = null
        session.calibrationReferenceMm = null
        calibration.reset()
        calibrationTapPoints.clear()
        setActiveTool(Tool.NONE)

        txtScanStatus.text = "Loaded: $name"
        txtCalStatus.text = "Not calibrated"
        refreshLandmarkSpinners()
        refreshResults()
        updateOverlay()
        toast("Scan loaded — recalibrate and re-place landmarks for this model.")
    }

    private fun applyCalibration() {
        if (calibrationTapPoints.size < 2) { toast("Place 2 calibration points first."); return }
        val mm = editCalMm.text.toString().toFloatOrNull()
        if (mm == null || mm <= 0f) { toast("Enter a valid mm distance."); return }
        calibration.set(calibrationTapPoints[0], calibrationTapPoints[1], mm)
        session.calibrationScale = calibration.modelUnitsToMm
        session.calibrationReferenceMm = mm
        txtCalStatus.text = "Calibrated: ${"%.4f".format(calibration.modelUnitsToMm)} mm/unit"
    }

    private fun refreshLandmarkSpinners() {
        val names = session.landmarks.mapIndexed { i, lm -> "${lm.name} #${i + 1}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        listOf(spinnerAxisA1, spinnerAxisA2, spinnerAxisB1, spinnerAxisB2, spinnerDistFrom, spinnerDistTo).forEach {
            it.adapter = adapter
        }
        txtLandmarks.text = if (session.landmarks.isEmpty()) "No landmarks placed yet."
        else session.landmarks.joinToString("\n") {
            "• ${it.name}  (${"%.3f".format(it.position.x)}, ${"%.3f".format(it.position.y)}, ${"%.3f".format(it.position.z)})"
        }
    }

    private fun selectedLandmark(spinner: Spinner): Landmark? {
        val i = spinner.selectedItemPosition
        return if (i in session.landmarks.indices) session.landmarks[i] else null
    }

    private fun computeAngle() {
        val a1 = selectedLandmark(spinnerAxisA1); val a2 = selectedLandmark(spinnerAxisA2)
        val b1 = selectedLandmark(spinnerAxisB1); val b2 = selectedLandmark(spinnerAxisB2)
        if (a1 == null || a2 == null || b1 == null || b2 == null) { toast("Select two landmarks for each axis."); return }
        if (a1.id == a2.id || b1.id == b2.id) { toast("Each axis needs two different landmarks."); return }

        val vA = a2.position - a1.position
        val vB = b2.position - b1.position
        val rawAngle = AngleEngine.roundHalfDegree(AngleEngine.angleBetween3D(vA, vB))

        val planeChoice = planes[spinnerPlane.selectedItemPosition]
        val target = editTarget.text.toString().toFloatOrNull() ?: 0f
        val tolerance = editTolerance.text.toString().toFloatOrNull() ?: 1.5f

        var planeAngleDeg: Float? = null
        val deviationFromNeutral: Float
        if (planeChoice == "Raw 3D (no projection)") {
            deviationFromNeutral = AngleEngine.roundHalfDegree(90f - rawAngle)
        } else {
            val plane = when (planeChoice) {
                "Sagittal" -> AngleEngine.Plane.SAGITTAL
                "Frontal" -> AngleEngine.Plane.FRONTAL
                else -> AngleEngine.Plane.TRANSVERSE
            }
            val signed = AngleEngine.roundHalfDegree(abs(AngleEngine.signedPlaneAngle(vA, vB, plane)))
            planeAngleDeg = signed
            deviationFromNeutral = AngleEngine.roundHalfDegree(90f - signed)
        }
        val deviation = AngleEngine.round1(deviationFromNeutral - target)
        val status = when {
            abs(deviation) <= tolerance * 0.7f -> "PASS"
            abs(deviation) <= tolerance -> "PASS (near tolerance)"
            else -> "REVIEW REQUIRED"
        }

        session.angleMeasurements.add(
            AngleMeasurement(
                axisALabel = "${a1.name} \u2192 ${a2.name}",
                axisBLabel = "${b1.name} \u2192 ${b2.name}",
                rawAngleDeg = rawAngle,
                plane = if (planeChoice == "Raw 3D (no projection)") null else planeChoice,
                planeAngleDeg = planeAngleDeg,
                target = target, tolerance = tolerance, deviation = deviation, status = status
            )
        )
        refreshResults()
    }

    private fun computeDistance() {
        val from = selectedLandmark(spinnerDistFrom); val to = selectedLandmark(spinnerDistTo)
        if (from == null || to == null || from.id == to.id) { toast("Select two different landmarks."); return }
        val modelDist = from.position.distanceTo(to.position)
        val mm = calibration.toMm(modelDist)?.let { AngleEngine.round1(it) }
        session.distanceMeasurements.add(DistanceMeasurement(from.name, to.name, modelDist, mm))
        refreshResults()
    }

    private fun refreshResults() {
        val sb = StringBuilder()
        session.angleMeasurements.forEach {
            sb.append("ANGLE  ${it.axisALabel}   vs   ${it.axisBLabel}\n")
            sb.append("  raw 3D: ${it.rawAngleDeg}\u00B0")
            it.planeAngleDeg?.let { pa -> sb.append("   ${it.plane}: $pa\u00B0") }
            sb.append("\n  target ${it.target}\u00B0  tol \u00B1${it.tolerance}\u00B0  dev ${if (it.deviation > 0) "+" else ""}${it.deviation}\u00B0  [${it.status}]\n\n")
        }
        session.distanceMeasurements.forEach {
            sb.append("DISTANCE  ${it.fromLabel} \u2192 ${it.toLabel}\n")
            sb.append("  ${it.modelDistance} model units" + (it.mm?.let { mm -> "  =  $mm mm" } ?: "  (not calibrated)") + "\n\n")
        }
        txtResults.text = if (sb.isEmpty()) "No measurements yet." else sb.toString()
    }

    private fun updateOverlay() {
        val axisLines = ArrayList<Pair<Vec3, Vec3>>()
        if (calibrationTapPoints.size == 2) axisLines.add(calibrationTapPoints[0] to calibrationTapPoints[1])
        renderer.updateOverlay(session.landmarks, axisLines)
    }

    private fun saveScan() {
        try {
            val dir = getExternalFilesDir(null) ?: filesDir
            val file = File(dir, "castiq_scan_${session.scanId}.json")
            FileWriter(file).use { it.write(session.toJson().toString(2)) }
            toast("Saved: ${file.absolutePath}")
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
