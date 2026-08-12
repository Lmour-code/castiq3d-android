package com.castiq.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RayPickerTest {

    private val triangle = listOf(
        Vec3(-1f, -1f, 0f), Vec3(1f, -1f, 0f), Vec3(0f, 1f, 0f)
    )

    @Test
    fun rayHitsTriangleDirectlyAhead() {
        val hit = RayPicker.pick(Vec3(0f, 0f, -5f), Vec3(0f, 0f, 1f), triangle)
        assertNotNull(hit)
        assertEquals(5f, hit!!.distance, 0.01f)
    }

    @Test
    fun rayMissesTriangleOutsideBounds() {
        val hit = RayPicker.pick(Vec3(5f, 5f, -5f), Vec3(0f, 0f, 1f), triangle)
        assertNull(hit)
    }

    @Test
    fun picksClosestOfTwoOverlappingTriangles() {
        val near = listOf(Vec3(-1f, -1f, -2f), Vec3(1f, -1f, -2f), Vec3(0f, 1f, -2f))
        val far = listOf(Vec3(-1f, -1f, 2f), Vec3(1f, -1f, 2f), Vec3(0f, 1f, 2f))
        val hit = RayPicker.pick(Vec3(0f, 0f, -10f), Vec3(0f, 0f, 1f), near + far)
        assertNotNull(hit)
        assertEquals(8f, hit!!.distance, 0.01f) // -10 to -2
    }

    @Test
    fun rayPointingAwayFromTriangleDoesNotHit() {
        val hit = RayPicker.pick(Vec3(0f, 0f, -5f), Vec3(0f, 0f, -1f), triangle)
        assertNull(hit)
    }
}
