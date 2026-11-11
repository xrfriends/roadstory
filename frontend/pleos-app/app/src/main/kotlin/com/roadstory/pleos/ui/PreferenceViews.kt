package com.roadstory.pleos.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import android.widget.LinearLayout
import com.roadstory.pleos.R

fun Context.surface(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radius).toFloat()
    if (stroke != null) setStroke(dp(1), stroke)
}

fun Context.iconButton(icon: StoryIcon, description: String, size: Int = 80, onClick: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = description
        background = surface(RoadStoryPalette.raisedSurface, 24)
        addView(RoadStoryIcon(context, icon), LinearLayout.LayoutParams(dp(size / 3), dp(size / 3)))
        setOnClickListener { onClick() }
        setOnFocusChangeListener { _, focused ->
            background = surface(RoadStoryPalette.raisedSurface, 24, if (focused) RoadStoryPalette.focus else null)
        }
    }

fun Context.appMenuRow(entry: VehicleAppMenuEntry, selected: Boolean, onClick: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
        isClickable = true
        isFocusable = true
        isSelected = selected
        contentDescription = entry.label
        stateDescription = if (selected) "현재 화면" else null
        fun repaint(focused: Boolean) {
            background = surface(if (selected) RoadStoryPalette.accentSurface else RoadStoryPalette.raisedSurface, 16,
                if (focused) RoadStoryPalette.focus else null)
        }
        repaint(false)
        val kind = when (entry.screen) {
            VehicleScreen.HOME -> StoryIcon.HOME
            VehicleScreen.PLAYER -> StoryIcon.HEADPHONES
            VehicleScreen.COLLECTION -> StoryIcon.BOOKMARK
            else -> StoryIcon.SETTINGS
        }
        addView(RoadStoryIcon(context, kind), LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(label(entry.label, 33f, RoadStoryPalette.text).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(0, dp(80), 1f).apply { marginStart = dp(28) })
        if (selected) addView(RoadStoryIcon(context, StoryIcon.CHECK), LinearLayout.LayoutParams(dp(40), dp(40)))
        setOnClickListener { onClick() }
        setOnFocusChangeListener { _, focused -> repaint(focused) }
    }

class PreferenceSwitch(context: Context, checked: Boolean, description: String, private val changed: (Boolean) -> Unit) : View(context), Checkable {
    private var value = checked
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle(); changed(value) }
    }
    override fun isChecked() = value
    override fun setChecked(checked: Boolean) { value = checked; invalidate() }
    override fun toggle() { isChecked = !value }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = value
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val trackWidth = context.dp(68).toFloat()
        val trackHeight = context.dp(42).toFloat()
        val left = (width - trackWidth) / 2
        val top = (height - trackHeight) / 2
        ink.color = if (value) RoadStoryPalette.active else RoadStoryPalette.raisedSurface
        canvas.drawRoundRect(left, top, left + trackWidth, top + trackHeight, context.dp(14).toFloat(), context.dp(14).toFloat(), ink)
        ink.color = if (value) RoadStoryPalette.text else RoadStoryPalette.tertiaryText
        val thumbLeft = left + context.dp(if (value) 34 else 6)
        canvas.drawRoundRect(thumbLeft, top + context.dp(7), thumbLeft + context.dp(28), top + context.dp(35), context.dp(7).toFloat(), context.dp(7).toFloat(), ink)
        if (isFocused) {
            ink.style = Paint.Style.STROKE; ink.strokeWidth = context.dp(2).toFloat(); ink.color = RoadStoryPalette.focus
            canvas.drawRoundRect(1f, 1f, width - 1f, height - 1f, context.dp(16).toFloat(), context.dp(16).toFloat(), ink)
            ink.style = Paint.Style.FILL
        }
    }
}

class FrequencyChoice(context: Context, private val label: String, selected: Boolean, description: String, onClick: () -> Unit) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.resources.getFont(R.font.asta_sans) }
    init {
        isSelected = selected
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.RadioButton"
        info.isCheckable = true
        info.isChecked = isSelected
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = context.dp(16).toFloat()
        val radius = context.dp(13).toFloat()
        ink.color = if (isSelected) RoadStoryPalette.text else RoadStoryPalette.tertiaryText
        ink.style = if (isSelected) Paint.Style.FILL else Paint.Style.STROKE
        ink.strokeWidth = context.dp(2).toFloat()
        canvas.drawCircle(centerX, height / 2f, radius, ink)
        if (isSelected) { ink.color = RoadStoryPalette.surface; canvas.drawCircle(centerX, height / 2f, context.dp(6).toFloat(), ink) }
        ink.style = Paint.Style.FILL
        ink.color = RoadStoryPalette.secondaryText
        ink.textSize = 23 * resources.displayMetrics.scaledDensity
        val baseline = height / 2f - (ink.fontMetrics.ascent + ink.fontMetrics.descent) / 2
        canvas.drawText(label, context.dp(39).toFloat(), baseline, ink)
        if (isFocused) {
            ink.style = Paint.Style.STROKE; ink.color = RoadStoryPalette.focus
            canvas.drawRoundRect(1f,1f,width - 1f,height - 1f,context.dp(12).toFloat(),context.dp(12).toFloat(),ink)
            ink.style = Paint.Style.FILL
        }
    }
}
