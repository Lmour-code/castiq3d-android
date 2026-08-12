package com.castiq.geometry

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class AngleEngineTest {

    @Test
    fun perpendicularVectorsMeasure90Degrees() {
        val a = Vec3(0f, 1f, 0f)
        val b = Vec3(1f, 0f, 0f)
        assertEquals(90f, AngleEngine.angleBetween3D(a, b), 0.01f)
    }

    @Test
    fun parallelVectorsMeasure0Degrees() {
        val a = Vec3(1f, 0f, 0f)
        val b = Vec3(2f, 0f, 0f)
        assertEquals(0f, AngleEngine.angleBetween3D(a, b), 0.01f)
    }

    @Test
    fun oppositeVectorsMeasure180Degrees() {
        val a = Vec3(1f, 0f, 0f)
        val b = Vec3(-1f, 0f, 0f)
        assertEquals(180f, AngleEngine.angleBetween3D(a, b), 0.01f)
    }

    @Test
    fun known5DegreeTiltInSagittalPlane() {
        // leg axis straight up (Y-up convention)
        val legAxis = Vec3(0f, 1f, 0f)
        // foot axis tilted 5 degrees off horizontal, within the Z-Y (sagittal) plane
        val angleRad = Math.toRadians(5.0)
        val footAxis = Vec3(0f, sin(angleRad).toFloat(), cos(angleRad).toFloat())
        val signed = AngleEngine.signedPlaneAngle(legAxis, footAxis, AngleEngine.Plane.SAGITTAL)
        // leg (vertical) to foot (near-horizontal) should read close to 85 degrees
        assertEquals(85f, abs(signed), 0.5f)
    }

    @Test
    fun calibrationScalesModelUnitsToMillimetresCorrectly() {
        val cal = Calibration()
        cal.set(Vec3(0f, 0f, 0f), Vec3(2f, 0f, 0f), 50f) // 2 model units known to equal 50mm
        assertEquals(25f, cal.modelUnitsToMm!!, 0.001f)
        assertEquals(125f, cal.toMm(5f)!!, 0.01f) // 5 model units -> 125mm
    }

    @Test
    fun uncalibratedDistanceReturnsNullRatherThanGuessing() {
        val cal = Calibration()
        assertEquals(null, cal.toMm(10f))
    }

    @Test
    fun roundHalfDegreeRoundsToNearestHalf() {
        assertEquals(5.5f, AngleEngine.roundHalfDegree(5.3f), 0.001f)
        assertEquals(5.0f, AngleEngine.roundHalfDegree(5.24f), 0.001f)
        assertEquals(6.0f, AngleEngine.roundHalfDegree(5.8f), 0.001f)
    }
}
