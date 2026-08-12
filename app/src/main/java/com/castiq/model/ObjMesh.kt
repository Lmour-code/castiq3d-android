package com.castiq.model

import com.castiq.geometry.Vec3

/**
 * A loaded triangle mesh.
 * vertexData: interleaved (x,y,z,nx,ny,nz) per vertex, ready for glVertexAttribPointer.
 * triangles: flattened triangle soup in model space, for RayPicker (kept alongside, not
 *            derived from the GPU buffer, since picking happens on the CPU).
 */
class ObjMesh(
    val vertexData: FloatArray,
    val vertexCount: Int,
    val triangles: List<Vec3>
)
