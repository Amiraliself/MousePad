package com.example.mousepad

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

object Cfg {
    const val NAME = "mp"
    const val K_ENABLED = "enabled"
    const val K_ALPHA = "padAlpha"
    const val K_COLOR = "cursorColor"
    const val K_SHAPE = "cursorShape"
    const val K_SIZE = "cursorSize"
    const val K_SPEED = "speed"
    const val K_ACCEL = "accel"
    const val K_EDGE = "edge"
    const val K_HAPTIC = "haptic"

    const val D_ALPHA = 0
    const val D_COLOR = 0
    const val D_SHAPE = 0
    const val D_SIZE = 100
    const val D_SPEED = 150
}

object Shapes {
    const val COUNT = 4

    private val cache = HashMap<Int, Path>()

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.1f
    }

    private fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float): Path {
        val p = Path()
        p.addRoundRect(RectF(l, t, r, b), rad, rad, Path.Direction.CW)
        return p
    }

    private fun union(parts: List<Path>): Path {
        val result = Path(parts[0])
        for (i in 1 until parts.size) {
            result.op(parts[i], Path.Op.UNION)
        }
        return result
    }

    private fun build(shape: Int): Path {
        return when (shape) {
            1 -> union(
                listOf(
                    rr(-1.9f, 0f, 1.9f, 11f, 1.9f),
                    rr(-5.8f, 9f, 5.8f, 19.5f, 3.2f),
                    rr(1.7f, 6.8f, 4.4f, 12f, 1.3f),
                    rr(4.1f, 8f, 6.8f, 13f, 1.3f),
                    rr(-8.4f, 10.5f, -4.6f, 16.5f, 1.9f)
                )
            )
            2 -> union(
                listOf(
                    rr(-3f, -8.5f, 3f, -7.2f, 0.4f),
                    rr(-0.7f, -8.5f, 0.7f, 8.5f, 0.4f),
                    rr(-3f, 7.2f, 3f, 8.5f, 0.4f)
                )
            )
            3 -> union(
                listOf(
                    rr(-0.8f, -8.5f, 0.8f, 8.5f, 0.4f),
                    rr(-8.5f, -0.8f, 8.5f, 0.8f, 0.4f)
                )
            )
            else -> {
                val p = Path()
                p.moveTo(0f, 0f)
                p.lineTo(0f, 17f)
                p.lineTo(4.2f, 13.2f)
                p.lineTo(7.2f, 20.5f)
                p.lineTo(10.2f, 19.2f)
                p.lineTo(7.2f, 12.2f)
                p.lineTo(12.2f, 12.2f)
                p.close()
                p
            }
        }
    }

    fun paint(canvas: Canvas, shape: Int, scale: Float, black: Boolean) {
        val path = cache.getOrPut(shape) { build(shape) }
        fillP.color = if (black) Color.BLACK else Color.WHITE
        strokeP.color = if (black) Color.WHITE else Color.BLACK
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawPath(path, fillP)
        canvas.drawPath(path, strokeP)
        canvas.restore()
    }
}
