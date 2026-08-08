package com.castiq.model

import com.castiq.geometry.Vec3
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Minimal Wavefront OBJ loader: supports `v` and `f` lines (triangles or fan-triangulated
 * polygons). Per-triangle flat normals are computed from vertex positions rather than trusting
 * `vn` data, keeping this intentionally simple and dependency-free for Stage 1.
 */
object ObjLoader {

    fun load(input: InputStream): ObjMesh {
        val positions = ArrayList<Vec3>()
        val outVertices = ArrayList<Float>() // interleaved x,y,z,nx,ny,nz
        val triangleSoup = ArrayList<Vec3>()

        BufferedReader(InputStreamReader(input)).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "v" -> positions.add(Vec3(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()))
                    "f" -> {
                        // face tokens may be "v", "v/vt", "v//vn", or "v/vt/vn" — only the
                        // vertex index is needed since normals are recomputed per-triangle.
                        val idx = parts.drop(1).map { tok -> tok.split("/")[0].toInt() }
                        for (k in 1 until idx.size - 1) {
                            val pa = positions[idx[0] - 1]
                            val pb = positions[idx[k] - 1]
                            val pc = positions[idx[k + 1] - 1]
                            triangleSoup.add(pa); triangleSoup.add(pb); triangleSoup.add(pc)
                            val faceNormal = (pb - pa).cross(pc - pa).normalized()
                            for (p in listOf(pa, pb, pc)) {
                                outVertices.add(p.x); outVertices.add(p.y); outVertices.add(p.z)
                                outVertices.add(faceNormal.x); outVertices.add(faceNormal.y); outVertices.add(faceNormal.z)
                            }
                        }
                    }
                }
            }
        }
        val arr = FloatArray(outVertices.size)
        for (i in outVertices.indices) arr[i] = outVertices[i]
        return ObjMesh(arr, arr.size / 6, triangleSoup)
    }
}
