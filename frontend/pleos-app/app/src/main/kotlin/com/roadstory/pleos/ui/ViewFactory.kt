package com.roadstory.pleos.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.roadstory.pleos.R

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.verticalContainer(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(32), dp(24), dp(32), dp(32))
    setBackgroundColor(RoadStoryPalette.background)
}

fun Context.vehicleRoot(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundColor(RoadStoryPalette.background)
}

fun Context.pageNavigation(
    section: String,
    status: String = "주차 중",
    onBack: (() -> Unit)? = null,
    onMenu: ((View) -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
): LinearLayout = row().apply {
    // Preserve the Figma 1230 px canvas proportions inside the 1682 px expanded app pane.
    setPadding(dp(44), dp(33), dp(44), dp(33))
    minimumHeight = dp(176)
    background = roundedStroke(RoadStoryPalette.background, RoadStoryPalette.line, 0)
    if (onBack != null) {
        addView(circleAction("‹", 109, false, "뒤로", onBack), LinearLayout.LayoutParams(dp(109), dp(109)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(section, 29f, RoadStoryPalette.text, true))
            addView(label("로드스토리", 15f, RoadStoryPalette.tertiaryText))
        }, LinearLayout.LayoutParams(0, dp(109), 1f).apply { marginStart = dp(33) })
    } else {
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_road_story)
            contentDescription = "로드스토리 앱 아이콘"
            isEnabled = onMenu != null
            isClickable = onMenu != null
            isFocusable = onMenu != null
            setOnClickListener { onMenu?.invoke(this) }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = rounded(RoadStoryPalette.raisedSurface, 29)
            clipToOutline = true
        }, LinearLayout.LayoutParams(dp(109), dp(109)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isEnabled = onMenu != null
            isClickable = onMenu != null
            isFocusable = onMenu != null
            contentDescription = "앱 메뉴 · $section"
            stateDescription = if (onMenu != null) "접힘" else "주행 중 사용 불가"
            setOnClickListener { onMenu?.invoke(this) }
            addView(row().apply {
                addView(label("로드스토리", 44f, RoadStoryPalette.text, true))
                addView(RoadStoryIcon(context, StoryIcon.CHEVRON).apply { tag = "menuChevron" },
                    LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(25) })
            })
            addView(label(section, 22f, RoadStoryPalette.tertiaryText))
        }, LinearLayout.LayoutParams(0, dp(109), 1f).apply { marginStart = dp(33) })
    }
    addView(label("Music / Audio", 27f, RoadStoryPalette.tertiaryText).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }, LinearLayout.LayoutParams(dp(205), dp(109)))
    addView(row().apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        addView(RoadStoryIcon(context, StoryIcon.CAR).apply { color = RoadStoryPalette.secondaryText }, LinearLayout.LayoutParams(dp(33), dp(33)))
        addView(label(status, 29f, RoadStoryPalette.secondaryText), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(80)).apply { marginStart = dp(12) })
    }, LinearLayout.LayoutParams(dp(200), dp(109)).apply { marginStart = dp(25) })
    addView(iconButton(StoryIcon.SETTINGS, "설정", 109) { onSettings?.invoke() }.apply {
        isEnabled = onSettings != null
        isFocusable = onSettings != null
        alpha = if (isEnabled) 1f else 0.4f
    }, LinearLayout.LayoutParams(dp(109), dp(109)).apply { marginStart = dp(33) })
}

fun Context.screenContent(scrollable: Boolean = false): ViewGroup {
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(44), dp(44), dp(44), dp(44))
    }
    return if (scrollable) ScrollView(this).apply {
        isFillViewport = true
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    } else content
}

fun Context.miniPlayer(
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    storyTitle: String = "성수동, 이름에 담긴 두 개의 이야기",
    saved: Boolean = false,
    playing: Boolean = false,
    canSkipNext: Boolean = false,
    playbackTime: PlaybackUiState? = null,
): LinearLayout = row().apply {
    setPadding(dp(44), dp(22), dp(44), dp(22))
    background = roundedStroke(RoadStoryPalette.surface, RoadStoryPalette.line, 0)
    minimumHeight = dp(154)
    isClickable = true
    isFocusable = true
    val status = if (playbackTime?.offlineAudio == true) "오프라인 · 저장된 음원" else "최근 이야기 · 이어듣기"
    contentDescription = "$status, $storyTitle"
    setOnClickListener { onOpen() }
    addView(imagePanel(R.drawable.road_story_seongsu, null, 109), LinearLayout.LayoutParams(dp(109), dp(109)))
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(status, 25f, RoadStoryPalette.tertiaryText))
        addView(label(storyTitle, 30f, RoadStoryPalette.text, true))
    }, LinearLayout.LayoutParams(0, dp(109), 1f).apply { marginStart = dp(33) })
    addView(circleAction(if (playing) "Ⅱ" else "▶", 109, false, if (playing) "미니 플레이어 일시정지" else "미니 플레이어 재생", onToggle).apply {
        tag = "focus:mini-player:toggle"
    }, LinearLayout.LayoutParams(dp(109), dp(109)))
    addView(circleAction("▶|", 109, false, "미니 플레이어 다음", onNext).apply {
        tag = "focus:mini-player:next"
        disablePlaybackControlWhen(!canSkipNext)
    }, LinearLayout.LayoutParams(dp(109), dp(109)).apply { marginStart = dp(22) })
    addView(iconButton(StoryIcon.BOOKMARK, if (saved) "현재 이야기 저장 해제" else "현재 이야기 저장", 109, onSave).apply {
        tag = "focus:mini-player:save"
        isSelected = saved
    }, LinearLayout.LayoutParams(dp(109), dp(109)).apply { marginStart = dp(22) })
    if (playbackTime != null) {
        addView(listeningTime(playbackTime, 18f, showRemaining = false).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(dp(155), dp(109)))
    }
}

fun Context.appHeader(section: String, status: String = "주차 중", onHome: (() -> Unit)? = null): LinearLayout = row().apply {
    minimumHeight = dp(72)
    addView(ImageView(context).apply {
        setImageResource(R.drawable.ic_road_story)
        contentDescription = "로드스토리 앱 아이콘"
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }, LinearLayout.LayoutParams(dp(56), dp(56)))
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label("로드스토리", 20f, RoadStoryPalette.text, true))
        addView(label(section, 16f, RoadStoryPalette.tertiaryText))
        if (onHome != null) {
            isClickable = true
            isFocusable = true
            contentDescription = "홈으로 이동"
            setOnClickListener { onHome() }
        }
    }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(12) })
    addView(label("●  $status", 16f, RoadStoryPalette.secondaryText, true).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }, LinearLayout.LayoutParams(dp(132), dp(64)))
}

fun Context.row(gravityValue: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = gravityValue
}

fun Context.topLine(title: String, action: String? = null, onAction: (() -> Unit)? = null): LinearLayout = row().apply {
    minimumHeight = dp(48)
    addView(label(title, 18f, RoadStoryPalette.text, true), LinearLayout.LayoutParams(0, dp(48), 1f))
    if (action != null) {
        if (onAction != null) addView(quietAction(action, compact = true, onClick = onAction))
        else addView(label(action, 16f, RoadStoryPalette.secondaryText, true).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL })
    }
}

fun Context.heading(text: String, size: Float = 48f): TextView = label(text, size, RoadStoryPalette.text, true).apply {
    letterSpacing = -0.025f
    setLineSpacing(0f, 1.08f)
    setPadding(0, dp(4), 0, dp(16))
}

fun Context.eyebrow(text: String): TextView = label(text, 20f, RoadStoryPalette.tertiaryText, true).apply {
    letterSpacing = 0.08f
    setPadding(0, dp(4), 0, dp(8))
}

fun Context.body(text: String, size: Float = 26f): TextView = label(text, size, RoadStoryPalette.secondaryText).apply {
    setPadding(0, dp(2), 0, dp(12))
}

fun Context.label(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
    this.text = text
    styleLabel(size, color, bold)
}

private fun TextView.styleLabel(size: Float, color: Int, bold: Boolean = false) {
    textSize = size
    setTextColor(color)
    gravity = Gravity.CENTER_VERTICAL
    val asta = resources.getFont(R.font.asta_sans)
    typeface = Typeface.create(asta, if (bold) 700 else 400, false)
    includeFontPadding = false
}

internal fun Context.listeningTime(playback: PlaybackUiState, size: Float, showRemaining: Boolean = true): TextView =
    ListeningTimeTextView(this, playback, showRemaining).apply {
        styleLabel(size, RoadStoryPalette.secondaryText)
        tag = "playback:listening-time"
    }

private class ListeningTimeTextView(
    context: Context,
    private val playback: PlaybackUiState,
    private val showRemaining: Boolean,
) : TextView(context) {
    private val listeningSince = (if (playback.audioPositionMs != null) playback.audioPositionUpdatedAtMs
        else playback.listeningSinceElapsedMs)?.takeIf { playback.playing && it >= 0L }
    private val tick = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE) return
            val listenedMs = updateTimeText()
            postDelayed(this, 1_000L - listenedMs % 1_000L)
        }
    }

    init {
        updateTimeText()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        removeCallbacks(tick)
        if (isVisible && listeningSince != null) tick.run()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun updateTimeText(): Long {
        val activeMs = listeningSince?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) } ?: 0L
        val audioPosition = playback.audioPositionMs
        val audioDuration = playback.audioDurationMs
        if (audioPosition != null && audioDuration != null) {
            val position = audioPosition.coerceIn(0L, audioDuration)
            val elapsed = position + activeMs.coerceAtMost(audioDuration - position)
            val total = if (showRemaining) " / ${playbackDuration(audioDuration / 1_000L)}" else ""
            val updated = "재생 위치 ${playbackDuration(elapsed / 1_000L)}$total"
            if (text.toString() != updated) text = updated
            return elapsed
        }
        val accumulatedMs = playback.listenedMs.coerceAtLeast(0L)
        val listenedMs = accumulatedMs + activeMs.coerceAtMost(Long.MAX_VALUE - accumulatedMs)
        val estimatedMs = playback.currentDurationSeconds * 1_000L
        val hasRemainingNarration = playback.canPause || playback.resumeAtSentence || listenedMs == 0L
        val updated = buildString {
            append("들은 시간 ${playbackDuration(listenedMs / 1_000L)}")
            if (showRemaining && hasRemainingNarration && !playback.failed && estimatedMs > listenedMs) {
                val remainingSeconds = (estimatedMs - listenedMs + 999L) / 1_000L
                append("\n예상 남은 시간 ${playbackDuration(remainingSeconds)}")
            }
        }
        if (text.toString() != updated) text = updated
        return listenedMs
    }
}

fun Context.action(label: String, primary: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    textSize = 36f
    isAllCaps = false
    isFocusable = true
    minHeight = dp(VehicleUiPolicy.primaryActionMinHeightDp)
    setTextColor(if (primary) RoadStoryPalette.primaryDark else RoadStoryPalette.text)
    typeface = Typeface.create(resources.getFont(R.font.asta_sans), 700, false)
    includeFontPadding = false
    background = rounded(if (primary) RoadStoryPalette.primary else RoadStoryPalette.raisedSurface, 24)
    setOnClickListener { onClick() }
    installFocusBackground(primary)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(VehicleUiPolicy.primaryActionMinHeightDp)).apply {
        setMargins(0, dp(6), 0, dp(6))
    }
}

fun Context.quietAction(label: String, compact: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    textSize = if (compact) 25f else 33f
    isAllCaps = false
    isFocusable = true
    minWidth = dp(if (compact) 72 else 120)
    minHeight = dp(if (compact) 64 else VehicleUiPolicy.primaryActionMinHeightDp)
    setTextColor(RoadStoryPalette.text)
    typeface = Typeface.create(resources.getFont(R.font.asta_sans), 700, false)
    includeFontPadding = false
    background = rounded(RoadStoryPalette.raisedSurface, if (compact) 16 else 24)
    setOnClickListener { onClick() }
    installFocusBackground(false)
}

fun Context.modeAction(label: String, selected: Boolean, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    textSize = 20f
    isAllCaps = false
    isFocusable = true
    minHeight = dp(VehicleUiPolicy.modeActionMinHeightDp)
    setTextColor(if (selected) RoadStoryPalette.text else RoadStoryPalette.secondaryText)
    typeface = Typeface.create(resources.getFont(R.font.asta_sans), 700, false)
    includeFontPadding = false
    background = rounded(if (selected) RoadStoryPalette.accentSurface else RoadStoryPalette.surface, 16)
    setOnClickListener { onClick() }
    installFocusBackground(false)
}

fun Context.actionCard(title: String, detail: String, symbol: String, primary: Boolean, onClick: () -> Unit): LinearLayout = row().apply {
    isClickable = true
    isFocusable = true
    contentDescription = "$title, $detail"
    minimumHeight = dp(if (primary) 156 else 92)
    setPadding(dp(20), dp(16), dp(20), dp(16))
    background = rounded(if (primary) RoadStoryPalette.primary else RoadStoryPalette.raisedSurface, 24)
    val textColor = if (primary) RoadStoryPalette.primaryDark else RoadStoryPalette.text
    addView(label(symbol, 32f, textColor, true), LinearLayout.LayoutParams(dp(56), dp(56)))
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, if (primary) 28f else 24f, textColor, true))
        addView(label(detail, 20f, if (primary) RoadStoryPalette.primaryDark else RoadStoryPalette.secondaryText))
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(12) })
    addView(label("→", 24f, textColor))
    setOnClickListener { onClick() }
    setOnFocusChangeListener { view, focused -> view.background = rounded(if (focused) RoadStoryPalette.focus else if (primary) RoadStoryPalette.primary else RoadStoryPalette.raisedSurface, 24) }
}

fun Context.imagePanel(resourceId: Int, description: String?, height: Int): ImageView = ImageView(this).apply {
    setImageResource(resourceId)
    contentDescription = description
    scaleType = ImageView.ScaleType.CENTER_CROP
    background = rounded(RoadStoryPalette.raisedSurface, 24)
    clipToOutline = true
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
}

fun Context.circleAction(
    symbol: String,
    size: Int,
    primary: Boolean,
    description: String,
    onClick: () -> Unit,
): Button = Button(this).apply {
    text = symbol
    textSize = if (size >= 100) 40f else if (size >= 72) 32f else 20f
    isAllCaps = false
    isFocusable = true
    contentDescription = description
    setTextColor(if (primary) RoadStoryPalette.primaryDark else RoadStoryPalette.text)
    typeface = Typeface.create(resources.getFont(R.font.asta_sans), 700, false)
    includeFontPadding = false
    background = rounded(if (primary) RoadStoryPalette.primary else RoadStoryPalette.raisedSurface, size / 2)
    setOnClickListener { onClick() }
}

fun Context.imageCourseCard(
    resourceId: Int,
    tag: String,
    title: String,
    meta: String,
    onClick: () -> Unit,
): FrameLayout = FrameLayout(this).apply {
    isClickable = true
    isFocusable = true
    contentDescription = "$tag, $title, $meta"
    background = rounded(RoadStoryPalette.surface, 24)
    clipToOutline = true
    addView(ImageView(context).apply {
        setImageResource(resourceId)
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = null
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        setPadding(dp(20), dp(16), dp(20), dp(18))
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.argb(220, 19, 20, 23)),
        )
        addView(label(tag, 16f, RoadStoryPalette.text, true).apply {
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(Color.argb(180, 19, 20, 23), 12)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)))
        addGap(12)
        addView(label(title, 26f, RoadStoryPalette.text, true))
        addView(label(meta, 18f, RoadStoryPalette.secondaryText, true))
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    setOnClickListener { onClick() }
}

fun Context.tabStrip(labels: List<String>, selected: Int = 0): LinearLayout = row().apply {
    labels.forEachIndexed { index, value ->
        addView(label(value, 18f, if (index == selected) RoadStoryPalette.text else RoadStoryPalette.tertiaryText, index == selected).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            if (index == selected) background = roundedStroke(Color.TRANSPARENT, RoadStoryPalette.text, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
    }
}

fun Context.storyRow(
    title: String,
    place: String,
    duration: String,
    saved: Boolean,
    onSave: () -> Unit,
    onPlay: () -> Unit,
): LinearLayout = row().apply {
    setPadding(dp(20), dp(10), dp(12), dp(10))
    background = rounded(RoadStoryPalette.surface, 18)
    minimumHeight = dp(78)
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, 22f, RoadStoryPalette.text, true))
        addView(label(listOf(place, duration).filter(String::isNotBlank).joinToString("  ·  "), 17f, RoadStoryPalette.tertiaryText))
    }, LinearLayout.LayoutParams(0, dp(80), 1f))
    addView(circleAction(if (saved) "♥" else "♡", 80, false, "$title ${if (saved) "저장 해제" else "저장"}", onClick = onSave).apply {
        isSelected = saved
    }, LinearLayout.LayoutParams(dp(80), dp(80)))
    addView(circleAction("▶", 80, false, "$title 듣기", onPlay), LinearLayout.LayoutParams(dp(80), dp(80)).apply { marginStart = dp(8) })
}

fun Context.stat(value: String, label: String): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(12), dp(10), dp(12), dp(10))
    addView(this@stat.label(value, 30f, RoadStoryPalette.text, true))
    addView(this@stat.label(label, 18f, RoadStoryPalette.tertiaryText))
}

fun Context.courseCard(tag: String, title: String, description: String, meta: String, color: Int, onClick: () -> Unit): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.BOTTOM
    isClickable = true
    isFocusable = true
    contentDescription = "$title, $description, $meta"
    setPadding(dp(24), dp(22), dp(24), dp(22))
    background = gradient(color, RoadStoryPalette.surface, 24)
    addView(this@courseCard.label(tag, 18f, RoadStoryPalette.text, true).apply {
        setPadding(dp(10), 0, dp(10), 0)
        background = rounded(RoadStoryPalette.accentSurface, 14)
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))
    addGap(24)
    addView(this@courseCard.label(title, 30f, RoadStoryPalette.text, true))
    addView(this@courseCard.label(description, 22f, RoadStoryPalette.secondaryText))
    addView(this@courseCard.label(meta, 20f, RoadStoryPalette.text, true).apply { setPadding(0, dp(10), 0, 0) })
    setOnClickListener { onClick() }
    setOnFocusChangeListener { view, focused -> view.background = if (focused) rounded(RoadStoryPalette.focus, 24) else gradient(color, RoadStoryPalette.surface, 24) }
}

internal fun playbackDuration(seconds: Int): String = playbackDuration(seconds.toLong())

private fun playbackDuration(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

internal fun View.disablePlaybackControlWhen(disabled: Boolean) {
    if (!disabled) return
    isEnabled = false
    isFocusable = false
    setOnClickListener(null)
    isClickable = false
    alpha = 0.4f
}

private fun Context.rounded(color: Int, radius: Int) = GradientDrawable().apply {
    cornerRadius = dp(radius).toFloat()
    setColor(color)
}

private fun Context.roundedStroke(color: Int, stroke: Int, radius: Int) = rounded(color, radius).apply {
    setStroke(dp(1), stroke)
}

private fun Context.gradient(start: Int, end: Int, radius: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
    cornerRadius = dp(radius).toFloat()
}

private fun Button.installFocusBackground(primary: Boolean) {
    setOnFocusChangeListener { view, focused ->
        view.background = context.rounded(if (focused) RoadStoryPalette.focus else if (primary) RoadStoryPalette.primary else RoadStoryPalette.raisedSurface, 24)
    }
}

fun LinearLayout.addGap(value: Int = 12) = addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, context.dp(value)) })
