package com.castiq.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.castiq.geometry.Vec3
import com.castiq.model.Landmark
import com.castiq.model.ObjLoader
import com.castiq.model.ObjMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Stage 1 renderer. Deliberately plain OpenGL ES 2.0 (part of the Android SDK itself) rather
 * than a third-party 3D engine, so this file has no external Maven dependency that could be
 * stale or mismatched by build time.
 */
class CastGLRenderer(
    private val context: Context,
    private val assetPath: String
) : GLSurfaceView.Renderer {

    val camera = OrbitCamera()

    private var mesh: ObjMesh? = null
    private var meshBuffer: FloatBuffer? = null
    private var meshProgram = 0
    private var markerProgram = 0
    private var lineProgram = 0

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val overlayLock = Any()
    private var landmarksSnapshot: List<Landmark> = emptyList()
    private var axisLinesSnapshot: List<Pair<Vec3, Vec3>> = emptyList()
    private var previewPoint: Vec3? = null

    /** Live crosshair shown while the technician drags to fine-position a point before
     *  committing it — lets them see exactly where it'll land without their finger
     *  covering the target on a touchscreen. */
    fun setPreviewPoint(p: Vec3?) {
        synchronized(overlayLock) { previewPoint = p }
    }

    var onMeshLoaded: ((ObjMesh) -> Unit)? = null

    fun updateOverlay(landmarks: List<Landmark>, axisLines: List<Pair<Vec3, Vec3>>) {
        synchronized(overlayLock) {
            landmarksSnapshot = landmarks
            axisLinesSnapshot = axisLines
        }
    }

    fun currentMesh(): ObjMesh? = mesh
    fun currentViewMatrix() = viewMatrix
    fun currentProjMatrix() = projMatrix

    /**
     * Load a mesh from any InputStream (bundled asset OR a user-picked file, e.g. an .obj
     * exported from a phone photogrammetry scanning app). Must be called on the GL thread —
     * callers from the UI thread should wrap this in glSurfaceView.queueEvent { ... }, the
     * same way the activity does for user-picked files.
     */
    fun loadMesh(input: java.io.InputStream) {
        val loaded = ObjLoader.load(input)
        mesh = loaded
        meshBuffer = ByteBuffer.allocateDirect(loaded.vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(loaded.vertexData); position(0)
            }
        frameCameraToMesh(loaded)
        onMeshLoaded?.invoke(loaded)
    }

    /** Auto-frame the camera to whatever scale the loaded mesh actually is — a real scan
     *  imported from a phone scanning app could be any size, unlike the fixed-scale sample. */
    private fun frameCameraToMesh(m: ObjMesh) {
        if (m.vertexCount == 0) return
        var minX=Float.MAX_VALUE; var minY=Float.MAX_VALUE; var minZ=Float.MAX_VALUE
        var maxX=-Float.MAX_VALUE; var maxY=-Float.MAX_VALUE; var maxZ=-Float.MAX_VALUE
        var i = 0
        while (i < m.vertexData.size) {
            val x=m.vertexData[i]; val y=m.vertexData[i+1]; val z=m.vertexData[i+2]
            if (x<minX) minX=x; if (x>maxX) maxX=x
            if (y<minY) minY=y; if (y>maxY) maxY=y
            if (z<minZ) minZ=z; if (z>maxZ) maxZ=z
            i += 6 // stride: x,y,z,nx,ny,nz
        }
        val cx=(minX+maxX)/2f; val cy=(minY+maxY)/2f; val cz=(minZ+maxZ)/2f
        val extent = maxOf(maxX-minX, maxY-minY, maxZ-minZ, 0.01f)
        camera.target = Vec3(cx, cy, cz)
        camera.distance = extent * 2.2f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.03f, 0.035f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        meshProgram = ShaderProgram.compile(MESH_VS, MESH_FS)
        markerProgram = ShaderProgram.compile(POINT_VS, FLAT_FS)
        lineProgram = ShaderProgram.compile(LINE_VS, FLAT_FS)

        context.assets.open(assetPath).use { input -> loadMesh(input) }
        Matrix.setIdentityM(modelMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projMatrix, 0, 45f, aspect, 0.01f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val view = camera.computeViewMatrix()
        System.arraycopy(view, 0, viewMatrix, 0, 16)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

        drawMesh()

        val (lm, axes, preview) = synchronized(overlayLock) { Triple(landmarksSnapshot, axisLinesSnapshot, previewPoint) }
        drawMarkers(lm.map { it.position })
        drawLines(axes)
        preview?.let { drawMarkers(listOf(it), r = 0.94f, g = 0.66f, b = 0.24f, size = 26f) } // amber, larger — the live crosshair
    }

    private fun drawMesh() {
        val buffer = meshBuffer ?: return
        val vertexCount = mesh?.vertexCount ?: return
        GLES20.glUseProgram(meshProgram)
        val posLoc = GLES20.glGetAttribLocation(meshProgram, "aPosition")
        val normLoc = GLES20.glGetAttribLocation(meshProgram, "aNormal")
        val mvpLoc = GLES20.glGetUniformLocation(meshProgram, "uMVP")
        val colorLoc = GLES20.glGetUniformLocation(meshProgram, "uColor")

        buffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glEnableVertexAttribArray(posLoc)
        buffer.position(3)
        GLES20.glVertexAttribPointer(normLoc, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glEnableVertexAttribArray(normLoc)

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorLoc, 0.72f, 0.77f, 0.77f, 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(normLoc)
    }

    private fun drawMarkers(points: List<Vec3>, r: Float = 0.18f, g: Float = 0.83f, b: Float = 0.75f, size: Float = 20f) {
        if (points.isEmpty()) return
        val data = FloatArray(points.size * 3)
        points.forEachIndexed { i, p -> data[i * 3] = p.x; data[i * 3 + 1] = p.y; data[i * 3 + 2] = p.z }
        val buf = toBuffer(data)

        GLES20.glUseProgram(markerProgram)
        val posLoc = GLES20.glGetAttribLocation(markerProgram, "aPosition")
        val mvpLoc = GLES20.glGetUniformLocation(markerProgram, "uMVP")
        val colorLoc = GLES20.glGetUniformLocation(markerProgram, "uColor")
        val sizeLoc = GLES20.glGetUniformLocation(markerProgram, "uPointSize")

        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorLoc, r, g, b, 1f)
        GLES20.glUniform1f(sizeLoc, size)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, points.size)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun drawLines(segments: List<Pair<Vec3, Vec3>>) {
        if (segments.isEmpty()) return
        val data = FloatArray(segments.size * 6)
        segments.forEachIndexed { i, (a, b) ->
            data[i * 6] = a.x; data[i * 6 + 1] = a.y; data[i * 6 + 2] = a.z
            data[i * 6 + 3] = b.x; data[i * 6 + 4] = b.y; data[i * 6 + 5] = b.z
        }
        val buf = toBuffer(data)
        GLES20.glUseProgram(lineProgram)
        val posLoc = GLES20.glGetAttribLocation(lineProgram, "aPosition")
        val mvpLoc = GLES20.glGetUniformLocation(lineProgram, "uMVP")
        val colorLoc = GLES20.glGetUniformLocation(lineProgram, "uColor")
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorLoc, 0.94f, 0.34f, 0.35f, 1f) // red
        GLES20.glLineWidth(3f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, data.size / 3)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun toBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data); position(0)
        }

    companion object {
        private const val MESH_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                vNormal = aNormal;
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """
        private const val MESH_FS = """
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 vNormal;
            void main() {
                vec3 lightDir = normalize(vec3(0.4, 0.8, 0.5));
                float diffuse = max(dot(normalize(vNormal), lightDir), 0.0);
                float intensity = min(0.35 + diffuse * 0.8, 1.0);
                gl_FragColor = vec4(uColor.rgb * intensity, uColor.a);
            }
        """
        private const val POINT_VS = """
            uniform mat4 uMVP;
            uniform float uPointSize;
            attribute vec3 aPosition;
            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
            }
        """
        private const val LINE_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPosition;
            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """
        private const val FLAT_FS = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}
