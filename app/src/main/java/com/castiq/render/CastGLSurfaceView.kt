package com.castiq.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.castiq.geometry.Vec3
import kotlin.math.abs

/**
 * One finger drag = rotate. Two finger drag = pan. Pinch = zoom.
 * A tap that doesn't turn into a drag fires [onTapPick] with a world-space ray for the
 * activity to raycast against the current mesh.
 */
class CastGLSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private lateinit var castRenderer: CastGLRenderer
    var onTapPick: ((Pair<Vec3, Vec3>) -> Unit)? = null

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        pointerCount = event.pointerCount

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
                    val ndcX = (event.x / width) * 2f - 1f
                    val ndcY = 1f - (event.y / height) * 2f
                    // Note: reads the renderer's most recently published matrices from the UI
                    // thread. There is a small window where these lag one frame behind the GL
                    // thread; acceptable for an orbit camera that isn't animating on its own,
                    // but worth knowing if picking ever feels a frame "behind" during a fling.
                    val ray = castRenderer.camera.screenPointToRay(
                        ndcX, ndcY, castRenderer.currentViewMatrix(), castRenderer.currentProjMatrix()
                    )
                    onTapPick?.invoke(ray)
                }
            }
        }
        return true
    }

    companion object {
        private const val TAP_SLOP = 12f
        private const val ROTATE_SPEED = 0.008f
        private const val PAN_SPEED = 0.003f
    }
}
