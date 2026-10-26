package com.example.pitch_flip

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CoinGeometry {

    companion object {
        private const val SEGMENTS = 64
        private const val RADIUS = 0.48f
        private const val THICKNESS = 0.015f
        const val STRIDE_FLOATS = 8
    }

    val faceVertexCount = 2 * (SEGMENTS + 2)
    val edgeVertexCount = (SEGMENTS + 1) * 2
    val faceVertices: FloatBuffer = buildFaces()
    val edgeVertices: FloatBuffer = buildEdge()

    private fun buildFaces(): FloatBuffer {
        val data = FloatArray(faceVertexCount * STRIDE_FLOATS)
        var idx = 0

        for (side in 0 until 2) {
            val z = if (side == 0) THICKNESS else -THICKNESS
            val nz = if (side == 0) 1f else -1f

            data[idx++] = 0f
            data[idx++] = 0f
            data[idx++] = z
            data[idx++] = 0f
            data[idx++] = 0f
            data[idx++] = nz
            data[idx++] = 0.5f
            data[idx++] = 0.5f

            for (i in 0..SEGMENTS) {
                val angle = if (side == 0) {
                    2.0 * PI * i / SEGMENTS
                } else {
                    2.0 * PI * (SEGMENTS - i) / SEGMENTS
                }
                val x = (RADIUS * cos(angle)).toFloat()
                val y = (RADIUS * sin(angle)).toFloat()

                val u = (x / RADIUS + 1f) / 2f

                val v = if (side == 0) {
                    1f - (y / RADIUS + 1f) / 2f
                } else {
                    (y / RADIUS + 1f) / 2f
                }

                data[idx++] = x
                data[idx++] = y
                data[idx++] = z
                data[idx++] = 0f
                data[idx++] = 0f
                data[idx++] = nz
                data[idx++] = u
                data[idx++] = v
            }
        }

        return toBuffer(data)
    }

    private fun buildEdge(): FloatBuffer {
        val data = FloatArray(edgeVertexCount * STRIDE_FLOATS)
        var idx = 0

        for (i in 0..SEGMENTS) {
            val angle = 2.0 * PI * i / SEGMENTS
            val x = (RADIUS * cos(angle)).toFloat()
            val y = (RADIUS * sin(angle)).toFloat()
            val nx = cos(angle).toFloat()
            val ny = sin(angle).toFloat()

            val u = i.toFloat() / SEGMENTS

            data[idx++] = x
            data[idx++] = y
            data[idx++] = THICKNESS
            data[idx++] = nx
            data[idx++] = ny
            data[idx++] = 0f
            data[idx++] = u
            data[idx++] = 0f

            data[idx++] = x
            data[idx++] = y
            data[idx++] = -THICKNESS
            data[idx++] = nx
            data[idx++] = ny
            data[idx++] = 0f
            data[idx++] = u
            data[idx++] = 1f
        }

        return toBuffer(data)
    }

    private fun toBuffer(data: FloatArray): FloatBuffer {
        val buf = ByteBuffer
            .allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(data).position(0)
        return buf
    }
}
