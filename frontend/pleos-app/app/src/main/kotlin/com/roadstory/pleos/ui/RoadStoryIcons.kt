package com.roadstory.pleos.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

enum class StoryIcon { HOME, HEADPHONES, BOOKMARK, SETTINGS, CHECK, CHEVRON, CAR, SHIELD, HISTORY, COURSE }

/** Outline icons share one 24-unit viewport and rounded strokes, independent of font glyphs. */
class RoadStoryIcon(context: Context, val icon: StoryIcon) : View(context) {
    var color: Int = RoadStoryPalette.text
        set(value) { field = value; invalidate() }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val scale = minOf(width, height) / 24f
        canvas.translate((width - scale * 24) / 2, (height - scale * 24) / 2)
        canvas.scale(scale, scale)
        ink.color = color
        fun line(vararg points: Float) {
            val path = Path().apply {
                moveTo(points[0], points[1])
                for (index in 2 until points.size step 2) lineTo(points[index], points[index + 1])
            }
            canvas.drawPath(path, ink)
        }
        when (icon) {
            StoryIcon.HOME -> line(3f,10f,12f,3f,21f,10f,21f,21f,15f,21f,15f,14f,9f,14f,9f,21f,3f,21f,3f,10f)
            StoryIcon.HEADPHONES -> {
                canvas.drawArc(3f,3f,21f,21f,180f,180f,false,ink)
                canvas.drawRoundRect(3f,12f,7f,21f,1f,1f,ink)
                canvas.drawRoundRect(17f,12f,21f,21f,1f,1f,ink)
            }
            StoryIcon.BOOKMARK -> line(6f,3f,18f,3f,18f,21f,12f,17f,6f,21f,6f,3f)
            StoryIcon.SETTINGS -> {
                line(9f,3f,15f,3f,16f,6f,19f,7f,22f,12f,19f,17f,16f,18f,15f,21f,9f,21f,8f,18f,5f,17f,2f,12f,5f,7f,8f,6f,9f,3f)
                canvas.drawCircle(12f,12f,3.5f,ink)
            }
            StoryIcon.CHECK -> line(4f,12f,9f,17f,20f,6f)
            StoryIcon.CHEVRON -> line(5f,9f,12f,16f,19f,9f)
            StoryIcon.CAR -> {
                line(4f,10f,7f,4f,17f,4f,20f,10f)
                canvas.drawRoundRect(3f,10f,21f,19f,1f,1f,ink)
                line(4f,19f,4f,22f); line(20f,19f,20f,22f)
                line(6f,14f,8f,14f); line(16f,14f,18f,14f)
            }
            StoryIcon.SHIELD -> {
                line(12f,2f,21f,6f,21f,13f,18f,18f,12f,22f,6f,18f,3f,13f,3f,6f,12f,2f)
                line(8f,12f,11f,15f,17f,9f)
            }
            StoryIcon.HISTORY -> {
                canvas.drawArc(3f,3f,21f,21f,-150f,310f,false,ink)
                line(2f,3f,2f,9f,8f,9f); line(12f,7f,12f,12f,16f,14f)
            }
            StoryIcon.COURSE -> {
                canvas.drawCircle(5f,5f,2f,ink); canvas.drawCircle(19f,19f,2f,ink)
                line(8f,5f,17f,5f,20f,8f,17f,12f,7f,12f,4f,16f,7f,19f,16f,19f)
            }
        }
        canvas.restore()
    }
}
