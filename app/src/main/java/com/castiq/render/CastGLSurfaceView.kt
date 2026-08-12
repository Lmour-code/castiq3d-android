package com.castiq.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.castiq.geometry.Vec3
import kotlin.math.abs

/**
 * Two interaction modes, switched by [placementModeActive]:
 *
 * Navigation mode (placementModeActive = false): one finger drag = rotate, two finger
 * drag = pan, pinch = zoom, a tap that doesn't turn into a drag fires [onTapPick].
 *
 * Placement mode (placementModeActive = true, set by the activity while Calibrate/Landmark
 * tool is active): the finger no longer rotates the camera. Instead, dragging shows a live
 * crosshair offset above the actual touch point (so the fingertip doesn't cover the target)
 * via [onPlacementPreview], and lifting the finger commits that position via [onTapPick].
 */
class CastGLSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private lateinit var castRenderer: CastGLRenderer
    var onTapPick: ((Pair<Vec3, Vec3>) -> Unit)? = null
    var onPlacementPreview: ((Pair<Vec3, Vec3>) -> Unit)? = null
    var onPlacementCancel: (() -> Unit)? = null
    var placementModeActive: Boolean = false

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false
    private var pointerCount = 1

    private lateinit var scaleDetector: ScaleGestureDetector

    fun initRenderer(renderer: CastGLRenderer) {
        castRenderer = renderer
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                castRenderer.camera.zoom(1f / detector.scaleFactor)
                return true
            }
        })
    }

    private fun rayAt(screenX: Float, screenY: Float): Pair<Vec3, Vec3> {
        val ndcX = (screenX / width) * 2f - 1f
        val ndcY = 1f - (screenY / height) * 2f
        return castRenderer.camera.screenPointToRay(
            ndcX, ndcY, castRenderer.currentViewMatrix(), castRenderer.currentProjMatrix()
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        pointerCount = event.pointerCount

        if (placementModeActive) {
            // Offset the effective touch point upward so the crosshair sits above the
            // fingertip, not under it — the whole point of this mode.
            val effectiveY = event.y - PLACEMENT_OFFSET_PX
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    onPlacementPreview?.invoke(rayAt(event.x, effectiveY))
                }
                MotionEvent.ACTION_UP -> {
                    onTapPick?.invoke(rayAt(event.x, effectiveY))
                }
                MotionEvent.ACTION_CANCEL -> {
                    onPlacementCancel?.invoke()
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                downX = event.x; downY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (abs(event.x - downX) > TAP_SLOP || abs(event.y - downY) > TAP_SLOP) isDragging = true

                if (!scaleDetector.isInProgress) {
                    if (pointerCount >= 2) {
                        castRenderer.camera.pan(dx * PAN_SPEED, dy * PAN_SPEED)
                    } else {
                        castRenderer.camera.rotate(-dx * ROTATE_SPEED, dy * ROTATE_SPEED)
                    }
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && pointerCount == 1) {
                    // Note: reads the renderer's most recently published matrices from the UI
                    // thread. There is a small window where these lag one frame behind the GL
                    // thread; acceptable for an orbit camera that isn't animating on its own,
                    // but worth knowing if picking ever feels a frame "behind" during a fling.
                    onTapPick?.invoke(rayAt(event.x, event.y))
                }
            }
        }
        return true
    }

    companion object {
        private const val TAP_SLOP = 12f
        private const val ROTATE_SPEED = 0.008f
        private const val PAN_SPEED = 0.003f
        private const val PLACEMENT_OFFSET_PX = 90f // how far above the fingertip the crosshair sits
    }
}

