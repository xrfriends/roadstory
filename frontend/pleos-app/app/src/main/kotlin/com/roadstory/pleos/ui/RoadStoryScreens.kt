package com.roadstory.pleos.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.roadstory.pleos.R
import com.roadstory.pleos.platform.DrivingRestriction
import com.roadstory.pleos.platform.VehicleNavigationState
import com.roadstory.pleos.playback.CourseSummary
import com.roadstory.pleos.playback.DestinationMode
import com.roadstory.pleos.playback.PlaybackIssue
import com.roadstory.pleos.playback.PlaybackSource
import com.roadstory.pleos.playback.PlaylistBasis
import com.roadstory.pleos.playback.QueueStory
import com.roadstory.pleos.user.LibraryItem
import com.roadstory.pleos.user.LibrarySyncMode
import com.roadstory.pleos.user.LibrarySyncStatus
import com.roadstory.pleos.user.VehicleUserStore
import com.roadstory.pleos.user.ListeningFrequency

enum class VehicleScreen { HOME, FREE_DRIVE, PLAYER, DESTINATION, COURSES, COURSE_DETAIL, STORY_DETAIL, DRIVE_LIMIT, COLLECTION, SETTINGS }
data class PlaybackUiState(
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val canSkipNext: Boolean = false,
    val nextTitle: String? = null,
    val nextDurationSeconds: Int = 0,
    val currentStoryId: String? = null,
    val currentDurationSeconds: Int = 0,
    val endAfterCurrent: Boolean = false,
    val autoplayEnabled: Boolean = true,
    val queueEmpty: Boolean = false,
    val filteredByPreferences: Boolean = false,
    val source: PlaybackSource? = null,
    val issue: PlaybackIssue? = null,
    val errorMessage: String? = null,
    val currentStory: LibraryItem? = null,
    val listenedMs: Long = 0L,
    val listeningSinceElapsedMs: Long? = null,
    val resumeAtSentence: Boolean = false,
    val audioPositionMs: Long? = null,
    val audioDurationMs: Long? = null,
    val audioPositionUpdatedAtMs: Long? = null,
    val offlineAudio: Boolean = false,
    val downloadingAudio: Boolean = false,
) {
    val canPause: Boolean get() = playing || buffering

    fun displayedStory(fallback: LibraryItem): LibraryItem =
        if (canPause && endAfterCurrent && source == null) currentStory ?: fallback else fallback
}

class RoadStoryScreens(
    private val context: Context,
    private val navigate: (VehicleScreen) -> Unit,
    private val playbackAction: (String) -> Unit,
    private val dislikeStory: (LibraryItem) -> Unit,
    private val openAppMenu: (View) -> Unit,
    private val userStore: VehicleUserStore,
    private val refresh: () -> Unit,
    private val canUseParkedControls: () -> Boolean,
    private val journeyAction: (JourneyAction) -> Unit = {},
    private val openLibrarySignIn: () -> Unit = {},
    private val refreshLibrary: () -> Unit = {},
    private val signOutOfLibrary: () -> Unit = {},
    private val openGuestImport: () -> Unit = {},
    private val activeLibraryAccount: () -> String? = { null },
) {
    private var restriction = DrivingRestriction(true, 0)
    private var expanded = false
    private var settingsPage = 0
    private var collectionTab = 0
    private var playback = PlaybackUiState()
    private var journey = JourneyUiState()
    private var courseFilter = 0
    private var libraryStatus: LibrarySyncStatus? = null
    private var developmentSignInAvailable = false
    private var guestImportAvailable = false

    fun render(
        screen: VehicleScreen,
        restriction: DrivingRestriction,
        expanded: Boolean,
        playback: PlaybackUiState = PlaybackUiState(),
        journey: JourneyUiState = JourneyUiState(),
        libraryStatus: LibrarySyncStatus? = null,
        developmentSignInAvailable: Boolean = false,
        guestImportAvailable: Boolean = false,
    ): View {
        this.restriction = restriction
        this.expanded = expanded
        this.playback = playback
        this.journey = journey
        this.libraryStatus = libraryStatus
        this.developmentSignInAvailable = developmentSignInAvailable
        this.guestImportAvailable = guestImportAvailable
        return when (VehicleUiPolicy.permittedScreen(screen, restriction)) {
            VehicleScreen.HOME -> home(expanded)
            VehicleScreen.FREE_DRIVE -> freeDrive()
            VehicleScreen.PLAYER -> player(expanded)
            VehicleScreen.DESTINATION -> destination()
            VehicleScreen.COURSES -> courses()
            VehicleScreen.COURSE_DETAIL -> courseDetail()
            VehicleScreen.STORY_DETAIL -> storyDetail()
            VehicleScreen.DRIVE_LIMIT -> driveLimit()
            VehicleScreen.COLLECTION -> collection()
            VehicleScreen.SETTINGS -> settings()
        }
    }

    private fun collection() = scaffold("내 이야기", scrollable = true, screenId = "SCR-SAVED-001") {
        val owner = libraryStatus?.accountId
        addView(context.heading("다시 만나고 싶은 이야기.", 44f))
        addLibraryStatus()
        addGap(24)
        val counts = listOf(userStore.savedStories.size, userStore.savedCourses.size, userStore.history.size)
        addView(context.row().apply {
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
            background = context.surface(RoadStoryPalette.raisedSurface, 28)
            listOf("저장", "코스", "청취 기록").forEachIndexed { index, title ->
                addView(context.quietAction("$title ${counts[index]}") {
                    ifParked {
                        collectionTab = index
                        refresh()
                    }
                }.apply {
                    contentDescription = "$title 탭"
                    isSelected = collectionTab == index
                    background = context.surface(if (isSelected) RoadStoryPalette.accentSurface else RoadStoryPalette.raisedSurface, 20)
                }, weight(1f, 80))
            }
        }, matchHeight(96))
        addGap(24)
        val items = when (collectionTab) { 0 -> userStore.savedStories; 1 -> userStore.savedCourses; else -> userStore.history }
        if (items.isEmpty()) {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, context.dp(40), 0, context.dp(32))
                val icon = when (collectionTab) { 0 -> StoryIcon.BOOKMARK; 1 -> StoryIcon.COURSE; else -> StoryIcon.HISTORY }
                addView(RoadStoryIcon(context, icon).apply { color = RoadStoryPalette.tertiaryText }, fixed(48, 48))
                addGap(28)
                val title = when (collectionTab) {
                    0 -> "좋았던 이야기를 저장해 보세요."
                    1 -> "다시 달리고 싶은 코스를 저장해 보세요."
                    else -> "아직 들은 이야기가 없어요."
                }
                addView(context.label(title, 30f, RoadStoryPalette.text, true).apply { gravity = Gravity.CENTER })
                addGap(24)
                addView(context.label("지나간 길의 새로운 의미를 발견해 보세요.", 24f, RoadStoryPalette.secondaryText).apply { gravity = Gravity.CENTER })
                addGap(28)
                addView(context.action(if (collectionTab == 1) "테마 드라이브 둘러보기" else "주변 이야기 듣기", true) {
                    if (collectionTab == 1) {
                        ifParked { navigate(VehicleScreen.COURSES) }
                    } else {
                        startNearbyPlayback()
                    }
                }.apply { textSize = 25f }, fixed(if (collectionTab == 1) 360 else 280, 80))
            }, matchHeight(390))
        } else {
            items.forEach { item ->
                addView(context.row().apply {
                    background = context.surface(RoadStoryPalette.surface, 24)
                    setPadding(context.dp(24), context.dp(16), context.dp(24), context.dp(16))
                    addView(context.label(item.title, 28f, RoadStoryPalette.text, true), weight())
                    val isCourse = collectionTab == 1
                    val saved = if (isCourse) userStore.isCourseSaved(item.id) else userStore.isStorySaved(item.id)
                    addView(context.iconButton(StoryIcon.BOOKMARK, "${item.title} ${if (saved) "저장 해제" else "저장"}") {
                        ifParked {
                            if (activeLibraryAccount() == owner) {
                                if (isCourse) userStore.toggleCourse(item) else userStore.toggleStory(item)
                                refresh()
                            }
                        }
                    }.apply {
                        tag = "focus:library:${if (isCourse) "course" else "story"}:${item.id}:save"
                        isSelected = saved
                    }, fixed(80, 80))
                    addView(context.quietAction(if (isCourse) "코스 보기" else "듣기") {
                        ifParked {
                            if (activeLibraryAccount() == owner) {
                                if (isCourse) journeyAction(JourneyAction.OpenCourse(item.id))
                                else {
                                    playbackAction("play:${item.id}")
                                    navigate(VehicleScreen.PLAYER)
                                }
                            }
                        }
                    }, fixed(150, 80).apply { marginStart = context.dp(16) })
                }, matchHeight(112))
                addGap(12)
            }
        }
    }

    private fun LinearLayout.addLibraryStatus() {
        val status = libraryStatus
        val connected = status?.accountId != null
        val message = when (status?.mode) {
            LibrarySyncMode.SIGNING_IN -> "개발 계정을 확인하고 있어요."
            LibrarySyncMode.SYNCED -> "계정에 저장한 이야기입니다."
            LibrarySyncMode.PENDING -> "동기화 대기 ${status.pendingCount}개"
            LibrarySyncMode.OFFLINE -> if (connected) "연결되면 계정에 동기화합니다."
                else "서버에 연결하지 못했어요. 이 차량의 기록을 표시합니다."
            LibrarySyncMode.SIGN_IN_REQUIRED -> "계정을 다시 연결해 주세요. 지금은 이 차량의 기록을 표시합니다."
            LibrarySyncMode.GUEST, null -> "이 차량에서만 보이는 기록입니다."
        }
        addView(context.body(message, 24f))
        if (status != null && status.rejectedCount > 0) {
            addView(context.body("서버에서 저장할 수 없는 항목이 ${status.rejectedCount}개 있었어요.", 20f))
        }
        if (connected) {
            addGap(12)
            addView(context.row().apply {
                addView(context.quietAction("동기화") { ifParked(refreshLibrary) }, weight(1f, 80))
                addView(context.quietAction("로그아웃") { ifParked(signOutOfLibrary) },
                    weight(1f, 80).apply { marginStart = context.dp(12) })
            })
            if (guestImportAvailable) {
                addGap(8)
                addView(context.quietAction("비회원 저장 가져오기") { ifParked(openGuestImport) }, matchHeight(80))
            }
        } else if (developmentSignInAvailable && status?.mode != LibrarySyncMode.SIGNING_IN) {
            addGap(12)
            addView(context.quietAction("개발 계정 연결") { ifParked(openLibrarySignIn) }, matchHeight(80))
        }
    }

    private fun settings() = scaffold("설정", scrollable = true, screenId = "SCR-SETTINGS-001") {
        addView(context.heading("어떤 이야기를 들려드릴까요?", 44f))
        addView(context.body("개인화 설정은 주차 중에만 변경할 수 있습니다.", 24f))
        addGap(24)
        addView(LinearLayout(context).apply {
            orientation = if (expanded) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            addView(interestPanel(), if (expanded) weight(1.3f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(View(context), if (expanded) fixed(24, 1) else matchHeight(24))
            addView(listeningPanel(), if (expanded) weight(1f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun interestPanel() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(32), context.dp(28), context.dp(32), context.dp(24))
        background = context.surface(RoadStoryPalette.surface, 24)
        addView(context.label("관심 주제", 32f, RoadStoryPalette.text, true), matchHeight(56))
        addGap(16)
        VehicleUserStore.topics.drop(settingsPage * 4).take(4).forEach { topic ->
            addView(View(context).apply { setBackgroundColor(RoadStoryPalette.line) }, matchHeight(1))
            addView(context.row().apply {
                addView(context.label(topic.label, 24f, RoadStoryPalette.text, true), weight())
                ListeningFrequency.entries.forEach { choice ->
                    addView(FrequencyChoice(context, choice.label, userStore.frequency(topic) == choice, "${topic.label} · ${choice.label}") {
                        ifParked {
                            userStore.setFrequency(topic, choice)
                            refresh()
                        }
                    }, fixed(94, 96))
                }
            }, matchHeight(96))
        }
        addGap(12)
        addView(context.row().apply {
            addView(context.quietAction("이전") {
                if (settingsPage > 0 && canUseParkedControls()) {
                    settingsPage--
                    refresh()
                }
            }.apply {
                contentDescription = "관심 주제 이전 페이지"; isEnabled = settingsPage > 0; alpha = if (isEnabled) 1f else 0.35f
            }, fixed(110, 64))
            addView(context.label("${settingsPage + 1} / 3", 23f, RoadStoryPalette.secondaryText).apply { gravity = Gravity.CENTER }, weight())
            addView(context.quietAction("다음") {
                if (settingsPage < 2 && canUseParkedControls()) {
                    settingsPage++
                    refresh()
                }
            }.apply {
                contentDescription = "관심 주제 다음 페이지"; isEnabled = settingsPage < 2; alpha = if (isEnabled) 1f else 0.35f
            }, fixed(110, 64))
        })
    }

    private fun listeningPanel() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(32), context.dp(28), context.dp(32), context.dp(28))
        background = context.surface(RoadStoryPalette.surface, 24)
        addView(context.label("청취 설정", 32f, RoadStoryPalette.text, true), matchHeight(56))
        addGap(32)
        fun option(label: String, checked: Boolean, change: (Boolean) -> Unit) {
            addView(context.row().apply {
                addView(context.label(label, 25f, RoadStoryPalette.text), weight())
                addView(PreferenceSwitch(context, checked, label) { enabled ->
                    ifParked {
                        change(enabled)
                        refresh()
                    }
                }, fixed(80, 96))
            }, matchHeight(96))
            addView(View(context).apply { setBackgroundColor(RoadStoryPalette.line) }, matchHeight(1))
        }
        option("이야기 자동 재생", userStore.autoplay) { userStore.autoplay = it }
        option("가족 · 어린이 우선", userStore.familyMode) { userStore.familyMode = it }
        addGap(24)
        addView(context.row().apply {
            addView(RoadStoryIcon(context, StoryIcon.SHIELD).apply { color = RoadStoryPalette.secondaryText }, fixed(42, 42))
            addView(context.label("Music / Audio", 25f, RoadStoryPalette.secondaryText, true), weight().apply { marginStart = context.dp(16) })
        })
        addGap(16)
        addView(context.body("규제 중에는 듣기와 단순 조작만 제공합니다. 신호 대기 중에도 제한을 임의로 해제하지 않습니다.", 21f))
    }

    private fun home(@Suppress("UNUSED_PARAMETER") expanded: Boolean) = scaffold("홈", scrollable = true, screenId = "SCR-HOME-001") {
        addView(context.row(Gravity.TOP).apply {
            addView(context.label("지나치던 길이, 이야기로 바뀐는 순간.", 55f, RoadStoryPalette.text, true), weight())
            addView(context.label("⌖  차량 위치로 주변 탐색", 27f, RoadStoryPalette.secondaryText).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }, fixed(547, 115))
        }, matchHeight(115))
        addView(context.row(Gravity.TOP).apply {
            addView(nearbyCard(), weight(694f, 620))
            addView(context.rowSpacer(), LinearLayout.LayoutParams(context.dp(33), 1))
            addView(routeCard(), weight(448f, 620))
        })
        addGap(44)
        addView(context.eyebrow("TAKE THE SCENIC ROUTE"))
        addView(context.row().apply {
            addView(context.label("취향 따라, 테마 드라이브", 44f, RoadStoryPalette.text, true), weight())
            addView(context.quietAction("전체 보기", compact = true) { navigate(VehicleScreen.COURSES) }, fixed(205, 80))
        })
        addGap(33)
        addCourseResults(featured = true)
        addGap(33)
        addView(context.label("♢  주차 중에는 발견하고, 주행 중에는 듣기에 집중하세요.", 25f, RoadStoryPalette.tertiaryText))
    }

    private fun nearbyCard() = context.row(Gravity.CENTER_VERTICAL).apply {
        background = context.panel(RoadStoryPalette.raisedSurface, 24)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(44), context.dp(44), context.dp(44), context.dp(44))
            addView(context.label("현재 위치 주변 이야기", 33f, RoadStoryPalette.secondaryText))
            addGap(33)
            addView(context.label("지금 있는 곳의\n이야기를 찾아보세요.", 49f, RoadStoryPalette.text, true))
            addView(context.rowSpacer(), LinearLayout.LayoutParams(1, 0, 1f))
            addView(context.action("▶    주변 이야기 시작                    →", true, ::startNearbyPlayback).apply {
                tag = "focus:home:start-nearby"
            }, matchHeight(109))
        }, weight(494f, 620))
        addView(context.imagePanel(R.drawable.road_story_seongsu, null, 620), weight(201f, 620))
    }

    private fun routeCard() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(44), context.dp(44), context.dp(44), context.dp(44))
        background = context.panel(RoadStoryPalette.surface, 24)
        addView(context.label("⌁  목적지까지 이야기", 33f, RoadStoryPalette.secondaryText))
        addGap(33)
        val destination = (journey.navigation as? VehicleNavigationState.Guiding)?.destination
        addView(context.label(navigationStatus(), 27f, RoadStoryPalette.tertiaryText))
        addGap(10)
        if (destination != null) {
            addView(context.label(destination.name, 40f, RoadStoryPalette.text, true))
            addGap(24)
            val remaining = destination.remainingDurationSeconds
            addView(context.label(
                if (remaining == null) "남은 시간 확인 중" else "약 ${(remaining + 59) / 60}분 남음",
                28f, RoadStoryPalette.secondaryText,
            ))
        }
        addView(context.rowSpacer(), LinearLayout.LayoutParams(1, 0, 1f))
        addView(context.quietAction("이야기 편성 보기                 →") { navigate(VehicleScreen.DESTINATION) }, matchHeight(109))
    }

    private fun player(expanded: Boolean) = scaffold("지금 듣기", miniPlayer = false, scrollable = true, screenId = "SCR-PLAYER-001") {
        if (!expanded) setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(24))
        when (playback.issue) {
            PlaybackIssue.LOCATION_PERMISSION, PlaybackIssue.LOCATION_UNAVAILABLE -> {
                val missing = playback.issue == PlaybackIssue.LOCATION_PERMISSION
                addView(context.heading(
                    if (missing) "주변 이야기를 들으려면 위치 권한이 필요해요." else "현재 위치를 확인하지 못했어요.",
                    if (expanded) 44f else 32f,
                ))
                addGap(12)
                addView(context.action(if (missing) "위치 권한 요청" else "위치 다시 확인", true) {
                    playbackAction("start-nearby")
                }, matchHeight(80))
                if (missing) addView(context.quietAction("앱 권한 설정") { playbackAction("location-settings") }, matchHeight(80))
                addView(context.quietAction("내 이야기 보기") { navigate(VehicleScreen.COLLECTION) }, matchHeight(80))
                return@scaffold
            }
            PlaybackIssue.DESTINATION_UNAVAILABLE -> {
                addView(context.heading("목적지 안내 정보를 확인하지 못했어요.", 36f))
                addView(context.action("목적지 편성 다시 확인", true) { navigate(VehicleScreen.DESTINATION) }, matchHeight(80))
                return@scaffold
            }
            PlaybackIssue.OFFLINE_AUDIO_UNAVAILABLE -> {
                addView(context.heading("오프라인이에요.", 36f).apply { tag = "SCR-OFFLINE-001" })
                addView(context.body(playback.errorMessage ?: "차량에 저장한 음원을 들을 수 있어요.", 24f))
                addView(context.action("내 이야기 보기", true) { navigate(VehicleScreen.COLLECTION) }, matchHeight(80))
                addView(context.quietAction("다시 시도") { playbackAction("play") }, matchHeight(80))
                return@scaffold
            }
            null -> Unit
        }
        if (playback.queueEmpty) {
            if (playback.filteredByPreferences) {
                addView(context.heading("현재 설정에 맞는 이야기가 없어요.", if (expanded) 44f else 32f))
                addView(context.action("관심 주제 설정", true) { navigate(VehicleScreen.SETTINGS) }, matchHeight(80))
            } else {
                val empty = when (playback.source) {
                    PlaybackSource.COURSE -> "이 코스에서 들을 이야기가 없어요."
                    PlaybackSource.DESTINATION -> "목적지 주변에 들을 이야기가 없어요."
                    else -> "이 지역의 이야기는 아직 준비 중입니다."
                }
                addView(context.heading(empty, if (expanded) 44f else 32f))
            }
            addView(context.action("다시 확인", !playback.filteredByPreferences) { playbackAction("play") }, matchHeight(80))
            return@scaffold
        }

        val story = playback.displayedStory(userStore.currentStory)
        val transportSize = if (expanded) 109 else 80
        val playSize = if (expanded) 137 else 96
        val controlGap = if (expanded) 22 else 8
        addView(context.row().apply {
            addView(context.eyebrow("NOW PLAYING"), weight())
            val status = when {
                playback.offlineAudio -> "오프라인 · 저장된 음원"
                playback.playing -> "지금 듣는 이야기"
                playback.buffering -> "이야기 준비 중"
                else -> "이어들을 이야기"
            }
            addView(context.label(status, if (expanded) 30f else 20f, RoadStoryPalette.secondaryText).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            })
        }, matchHeight(38))
        addGap(if (expanded) 44 else 18)
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (story.subtitle.isNotBlank()) {
                addView(context.label(story.subtitle, if (expanded) 33f else 22f, RoadStoryPalette.secondaryText))
            }
            addView(context.heading(story.title, if (expanded) 54f else 36f))
            if (playback.currentStoryId == story.id) {
                addView(context.listeningTime(playback, 22f))
                if (playback.resumeAtSentence && !playback.canPause && !playback.failed) {
                    addView(context.body("이어듣기는 멈춘 문장부터 시작합니다.", 20f))
                }
            }
            addGap(18)
            addView(context.row(Gravity.CENTER).apply {
                addView(context.circleAction("|◀", transportSize, false, "이전 이야기", onClick = {}).apply {
                    disablePlaybackControlWhen(true)
                }, fixed(transportSize, transportSize))
                addView(context.circleAction(if (playback.canPause) "Ⅱ" else "▶", playSize, true, if (playback.canPause) "일시정지" else "재생") { playbackAction("toggle") }.apply {
                    tag = "focus:player:toggle"
                }, fixed(playSize, playSize).apply { setMargins(context.dp(controlGap), 0, context.dp(controlGap), 0) })
                addView(context.circleAction("▶|", transportSize, false, "다음 이야기") { playbackAction("next") }.apply {
                    tag = "focus:player:next"
                    disablePlaybackControlWhen(!playback.canSkipNext)
                }, fixed(transportSize, transportSize))
            }, matchHeight(playSize))
            addGap(18)
            addView(context.row(Gravity.CENTER).apply {
                addView(context.quietAction(if (userStore.isStorySaved(story.id)) "저장됨" else "저장") { playbackAction("save") }.apply {
                    textSize = if (expanded) 33f else 22f
                    tag = "focus:player:save"
                }, weight(1f, transportSize))
                addView(context.quietAction("즉시 종료") { playbackAction("stop") }.apply {
                    textSize = if (expanded) 33f else 22f
                    tag = "focus:player:stop"
                }, weight(1f, transportSize).apply { marginStart = context.dp(controlGap) })
                addView(context.circleAction("◈", transportSize, false, "현재 이야기 상세") { navigate(VehicleScreen.STORY_DETAIL) }, fixed(transportSize, transportSize).apply { marginStart = context.dp(controlGap) })
            })
            addGap(12)
            val disliked = userStore.isStoryDisliked(story.id)
            addView(context.quietAction(if (disliked) "관심 없음 ✓" else "관심 없음") { dislikeStory(story) }.apply {
                textSize = if (expanded) 33f else 22f
                contentDescription = "관심 없음"
                stateDescription = if (disliked) "선택됨" else null
                isSelected = disliked
                tag = "focus:player:dislike"
            }, matchHeight(80))
            addGap(12)
            addView(context.quietAction("자동 재생 종료") { ifParked { playbackAction("finish-after-current") } }.apply {
                tag = "focus:player:finish-after-current"
                isSelected = playback.endAfterCurrent
                disablePlaybackControlWhen(!playback.canPause || !playback.autoplayEnabled || !playback.canSkipNext || playback.endAfterCurrent)
            }, matchHeight(80))
            val autoplayStatus = when {
                playback.endAfterCurrent && playback.canPause -> "현재 이야기 후 자동 재생을 종료합니다."
                playback.endAfterCurrent -> "자동 재생 종료됨"
                !playback.autoplayEnabled -> "자동 재생 꺼짐"
                else -> null
            }
            autoplayStatus?.let { addView(context.body(it, 22f)) }
        }
        if (expanded) {
            addView(context.row(Gravity.TOP).apply {
                addView(albumArt(), weight(1f, 620))
                addView(controls, weight(1.25f).apply { marginStart = context.dp(44) })
            })
        } else {
            addView(controls)
        }
        addGap(22)
        addNextStoryPreview()
        if (!playback.offlineAudio && !playback.failed) {
            addView(context.quietAction(if (playback.downloadingAudio) "음원 저장 중" else "오프라인 저장") {
                ifParked { playbackAction("download-audio") }
            }.apply {
                tag = "focus:player:download-audio"
                disablePlaybackControlWhen(playback.downloadingAudio)
            }, matchHeight(80))
        }
        if (playback.failed) {
            addGap(22)
            addView(context.body(playback.errorMessage ?: "이야기를 재생하지 못했어요. 다시 시도해 주세요.", 25f))
            addView(context.quietAction("다시 재생") { playbackAction("play") }, fixed(240, 80))
        }
    }

    private fun LinearLayout.addNextStoryPreview() {
        val nextTitle = playback.nextTitle?.takeIf { it.isNotBlank() }
        if (!playback.canSkipNext || nextTitle == null) {
            if (!playback.buffering) addView(context.body("다음 이야기가 없어요.", 22f))
            return
        }
        addView(context.row().apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(context.label("다음 이야기", 17f, RoadStoryPalette.tertiaryText, true))
                addView(context.label(nextTitle, 22f, RoadStoryPalette.text, true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, weight())
            if (playback.nextDurationSeconds > 0) {
                addView(context.label("약 ${playbackDuration(playback.nextDurationSeconds)}", 18f, RoadStoryPalette.secondaryText, true).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }, fixed(110, 64))
            }
        })
    }

    private fun albumArt() = android.widget.FrameLayout(context).apply {
        background = context.panel(RoadStoryPalette.surface, 24)
        clipToOutline = true
        addView(context.imagePanel(R.drawable.road_story_seongsu, "성수동", 620), android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(context.label("♬  ROAD STORY", 25f, RoadStoryPalette.text, true).apply {
            gravity = Gravity.CENTER
            background = context.panel(android.graphics.Color.argb(214, 19, 20, 23), 15)
        }, android.widget.FrameLayout.LayoutParams(context.dp(278), context.dp(66), Gravity.START or Gravity.BOTTOM).apply {
            setMargins(context.dp(33), 0, 0, context.dp(33))
        })
    }

    private fun destination() = scaffold("목적지까지 이야기", onBack = { navigate(VehicleScreen.HOME) }, scrollable = true, screenId = "SCR-DEST-001") {
        val guidance = journey.navigation as? VehicleNavigationState.Guiding
        val destination = guidance?.destination
        val remaining = destination?.remainingDurationSeconds
        addView(context.heading(destination?.name ?: "목적지 주변 이야기", 38f))
        if (destination == null) {
            addGap(12)
            addView(context.body(navigationStatus(), 24f))
            addView(context.quietAction("내비 상태 다시 확인") { journeyAction(JourneyAction.RefreshDestination) }, matchHeight(80))
            return@scaffold
        }
        if (remaining == null || remaining < 60) {
            addGap(12)
            addView(context.body(if (remaining == null) "남은 시간을 아직 확인하지 못했어요." else "도착이 가까워 편성할 시간이 부족해요.", 24f))
            addView(context.quietAction("내비 상태 다시 확인") { journeyAction(JourneyAction.RefreshDestination) }, matchHeight(80))
            return@scaffold
        }
        addView(context.body("내비게이션 남은 시간 약 ${(remaining + 59) / 60}분", 22f))
        addGap(18)
        addView(context.row().apply {
            DestinationMode.entries.forEach { mode ->
                addView(context.modeAction(mode.label, journey.destinationMode == mode) {
                    journeyAction(JourneyAction.SetDestinationMode(mode))
                }, weight(1f, 72).apply { if (childCount > 0) marginStart = context.dp(8) })
            }
        })
        addGap(16)
        when (val result = journey.destinationPlan) {
            CatalogState.Idle, CatalogState.Loading -> addView(context.body("이야기를 편성하고 있어요.", 24f))
            CatalogState.Failed -> {
                addView(context.body("이야기 편성을 불러오지 못했어요.", 24f))
                addView(context.quietAction("편성 다시 확인") { journeyAction(JourneyAction.RefreshDestination) }, matchHeight(80))
            }
            is CatalogState.Ready -> {
                val plan = result.data
                val basis = if (plan.basis == PlaylistBasis.ROUTE) "안내 경로 이야기" else "목적지 주변 이야기"
                addView(context.heading(basis, 28f))
                if (plan.stories.isEmpty()) {
                    addView(context.body("이 모드로 남은 시간 안에 들을 이야기가 없어요.", 22f))
                } else {
                    addView(context.body("${plan.stories.size}개 · 예상 재생 ${playbackDuration(plan.totalAudioDurationSeconds)}", 22f))
                    addGap(12)
                    val mode = journey.destinationMode
                    plan.stories.forEach { story ->
                        addCatalogStory(story) { journeyAction(JourneyAction.PlayDestination(mode, story.id)) }
                    }
                    addView(context.action("▶  전체 재생", true) { journeyAction(JourneyAction.PlayDestination(mode)) }, matchHeight(80))
                }
            }
        }
    }

    private fun courses() = scaffold("테마 드라이브", onBack = { navigate(VehicleScreen.HOME) }, scrollable = true, screenId = "SCR-COURSE-LIST-001") {
        addView(context.heading("조금 다른 길, 새로운 발견.", 38f))
        addView(context.body("공개 코스의 장소와 이야기를 둘러보세요.", 22f))
        addGap(12)
        addView(context.row().apply {
            listOf("전체", "청취 1시간 이내", "가족").forEachIndexed { index, label ->
                addView(context.modeAction(label, courseFilter == index) {
                    ifParked {
                        courseFilter = index
                        refresh()
                    }
                }, weight(1f, 72).apply { if (childCount > 0) marginStart = context.dp(8) })
            }
        })
        addGap(12)
        addCourseResults(featured = false)
    }

    private fun LinearLayout.addCourseResults(featured: Boolean) {
        when (val result = journey.courses) {
            CatalogState.Idle, CatalogState.Loading -> addView(context.body("코스를 불러오고 있어요.", 24f))
            CatalogState.Failed -> {
                addView(context.body("코스를 불러오지 못했어요.", 24f))
                addView(context.quietAction("다시 확인") { journeyAction(JourneyAction.RefreshCourses) }, matchHeight(80))
            }
            is CatalogState.Ready -> {
                val courses = if (featured) result.data.take(2) else result.data.filter {
                    when (courseFilter) {
                        1 -> it.durationSeconds in 1..3600
                        2 -> it.familyFriendly
                        else -> true
                    }
                }
                if (courses.isEmpty()) addView(context.body("표시할 코스가 없어요.", 24f))
                courses.forEach { course ->
                    addView(context.imageCourseCard(
                        R.drawable.road_story_seongsu, "테마 드라이브", course.title, courseMetadata(course),
                    ) { journeyAction(JourneyAction.OpenCourse(course.id)) }.apply {
                        tag = "focus:course:${course.id}:open"
                    }, matchHeight(if (featured) 270 else 200))
                    addGap(12)
                }
            }
        }
    }

    private fun courseMetadata(course: CourseSummary) = buildList {
        add("장소 ${course.stopCount}곳")
        add("이야기 ${course.storyCount}개")
        if (course.durationSeconds > 0) add("청취 ${playbackDuration(course.durationSeconds)}")
        if (course.distanceMeters > 0) add("${course.distanceMeters / 1000.0}km")
    }.joinToString(" · ")

    private fun courseDetail() = scaffold("코스 상세", onBack = { navigate(VehicleScreen.COURSES) }, scrollable = true, screenId = "SCR-COURSE-DETAIL-001") {
        when (val result = journey.course) {
            CatalogState.Idle, CatalogState.Loading -> addView(context.heading("코스를 불러오고 있어요.", 34f))
            CatalogState.Failed -> {
                addView(context.heading("코스를 불러오지 못했어요.", 34f))
                addView(context.quietAction("코스 목록으로") { navigate(VehicleScreen.COURSES) }, matchHeight(80))
            }
            is CatalogState.Ready -> {
                val course = result.data
                val summary = course.summary
                val item = LibraryItem(summary.id, summary.title, courseMetadata(summary))
                addView(context.heading(summary.title, 38f))
                addView(context.body(summary.description, 24f))
                addView(context.body(courseMetadata(summary), 22f))
                addGap(16)
                course.stops.forEach { stop ->
                    addView(context.heading("${stop.sequence}. ${stop.placeName}", 28f))
                    stop.stories.forEach { story ->
                        addCatalogStory(story) { journeyAction(JourneyAction.PlayCourse(summary.id, story.id)) }
                    }
                    addGap(12)
                }
                val saved = userStore.isCourseSaved(summary.id)
                addView(context.row().apply {
                    if (course.stories.isNotEmpty()) {
                        addView(context.action("▶  코스 이야기 듣기", true) {
                            journeyAction(JourneyAction.PlayCourse(summary.id))
                        }.apply { tag = "focus:course:${summary.id}:play" }, weight(1f, 80))
                    }
                    addView(context.iconButton(StoryIcon.BOOKMARK, if (saved) "코스 저장 해제" else "코스 저장", 72) {
                        ifParked { userStore.toggleCourse(item); refresh() }
                    }.apply {
                        isSelected = saved
                        tag = "focus:course:${summary.id}:save"
                    }, fixed(80, 80).apply { marginStart = context.dp(12) })
                })
                if (course.stops.isNotEmpty()) {
                    addGap(12)
                    addView(context.quietAction("첫 장소 길 안내") {
                        journeyAction(JourneyAction.GuideFirstStop(summary.id))
                    }.apply { tag = "focus:course:${summary.id}:guide" }, matchHeight(80))
                }
            }
        }
    }

    private fun LinearLayout.addCatalogStory(story: QueueStory, play: () -> Unit) {
        val duration = story.durationSeconds.takeIf { it > 0 }?.let(::playbackDuration).orEmpty()
        addView(context.storyRow(story.title, story.placeName, duration, userStore.isStorySaved(story.id), onSave = {
            ifParked {
                userStore.toggleStory(LibraryItem(story.id, story.title, story.placeName, story.category))
                refresh()
            }
        }, onPlay = play))
        addGap(8)
    }

    private fun navigationStatus(): String = when (journey.navigation) {
        VehicleNavigationState.Loading -> "내비게이션 상태를 확인하고 있어요."
        VehicleNavigationState.Unavailable -> "내비게이션 연결을 확인할 수 없어요."
        VehicleNavigationState.NoDestination -> "내비게이션에서 목적지 안내를 시작해 주세요."
        is VehicleNavigationState.Guiding -> "내비게이션에서 받은 목적지"
    }

    private fun freeDrive() = scaffold("주변 이야기", onBack = { navigate(VehicleScreen.HOME) }, miniPlayer = false, screenId = "SCR-FREE-001") {
        addView(context.heading("현재 위치 주변의 이야기", 42f))
        addView(context.action("▶  이야기 시작", true, ::startNearbyPlayback))
    }

    private fun storyDetail() = scaffold("이야기 상세", onBack = { navigate(VehicleScreen.PLAYER) }, miniPlayer = false, screenId = "SCR-M-STORY-001") {
        val story = playback.displayedStory(userStore.currentStory)
        addView(context.heading(story.title, 40f))
        if (story.subtitle.isNotBlank()) addView(context.body(story.subtitle, 22f))
    }

    private fun driveLimit() = context.vehicleRoot().apply {
        contentDescription = "SCR-DRIVE-LIMIT-001"
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        gravity = Gravity.CENTER_VERTICAL
        val singleRow = resources.configuration.screenWidthDp >= 600
        val horizontalPadding = context.dp(if (expanded) 44 else 24)
        val verticalPadding = context.dp(if (singleRow) 16 else 12)
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        val story = playback.displayedStory(userStore.currentStory)
        val saved = userStore.isStorySaved(story.id)
        val disliked = userStore.isStoryDisliked(story.id)
        addView(context.label("주행 제한 중", 22f, RoadStoryPalette.secondaryText, true))
        addGap(6)
        addView(context.label(story.title, if (expanded) 48f else 32f, RoadStoryPalette.text, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addGap(6)
        val status = when {
            playback.queueEmpty -> "들을 이야기가 없어요."
            playback.issue == PlaybackIssue.OFFLINE_AUDIO_UNAVAILABLE -> "오프라인 · 음원 없음"
            playback.offlineAudio -> "오프라인 음원"
            playback.failed -> "이야기를 재생하지 못했어요."
            playback.buffering -> "재생 준비 중"
            playback.playing -> "재생 중"
            else -> "재생 대기 중"
        }
        addView(context.label(status, 22f, RoadStoryPalette.secondaryText))
        if (playback.currentStoryId == story.id) {
            addGap(4)
            addView(context.listeningTime(playback, 20f, showRemaining = singleRow))
        }
        addGap(16)

        fun LinearLayout.addControl(label: String, command: String, description: String = label, selected: Boolean = false) {
            val control = context.action(label, primary = command == "toggle") {
                if (command == "dislike") dislikeStory(story) else playbackAction(command)
            }.apply {
                textSize = if (expanded) 28f else 22f
                setPadding(context.dp(4), context.dp(8), context.dp(4), context.dp(8))
                maxLines = 2
                contentDescription = description
                isSelected = selected
                stateDescription = if (selected) "선택됨" else null
                tag = "focus:player:$command"
                disablePlaybackControlWhen(command == "next" && !playback.canSkipNext)
            }
            addView(control, weight().apply { if (childCount > 0) marginStart = context.dp(12) })
        }

        val playbackControls = context.row().apply {
            addControl(if (playback.canPause) "일시정지" else "재생", "toggle")
            addControl("다음", "next", "다음 이야기")
        }
        addView(playbackControls)
        val secondaryControls = if (singleRow) playbackControls else context.row().also {
            addGap(8)
            addView(it)
        }
        secondaryControls.addControl(if (saved) "저장됨" else "저장", "save", if (saved) "저장 해제" else "저장", saved)
        secondaryControls.addControl(if (disliked) "관심 없음 ✓" else "관심 없음", "dislike", "관심 없음", disliked)
        secondaryControls.addControl("종료", "stop")
    }

    private inline fun ifParked(action: () -> Unit) {
        if (canUseParkedControls()) action()
    }

    private fun startNearbyPlayback() = ifParked {
        playbackAction("start-nearby")
        navigate(VehicleScreen.PLAYER)
    }

    private fun scaffold(
        section: String,
        onBack: (() -> Unit)? = null,
        miniPlayer: Boolean = true,
        scrollable: Boolean = false,
        screenId: String,
        content: LinearLayout.() -> Unit,
    ): LinearLayout = context.vehicleRoot().apply {
        contentDescription = screenId
        val canOpenMenu = VehicleAppMenu.availableEntries(restriction).isNotEmpty()
        addView(context.pageNavigation(
            section = section,
            status = if (restriction.blocksDetailedContent) "주행 제한 중" else "주차 중",
            onBack = onBack,
            onMenu = if (canOpenMenu) openAppMenu else null,
            onSettings = if (canOpenMenu) ({ navigate(VehicleScreen.SETTINGS) }) else null,
        ))
        val body = context.screenContent(scrollable)
        val bodyLayout = if (body is ScrollView) body.getChildAt(0) as LinearLayout else body as LinearLayout
        bodyLayout.content()
        addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (miniPlayer) {
            val story = playback.displayedStory(userStore.currentStory)
            addView(context.miniPlayer(
                onOpen = { navigate(VehicleScreen.PLAYER) },
                onToggle = { playbackAction("toggle") },
                onNext = { playbackAction("next") },
                onSave = { playbackAction("save") },
                storyTitle = story.title,
                saved = userStore.isStorySaved(story.id),
                playing = playback.canPause,
                canSkipNext = playback.canSkipNext,
                playbackTime = playback.takeIf { it.currentStoryId == story.id },
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(154)))
        }
    }

    private fun weight(value: Float = 1f, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(0, if (height > 0) context.dp(height) else height, value)
    private fun fixed(width: Int, height: Int) = LinearLayout.LayoutParams(context.dp(width), context.dp(height))
    private fun matchHeight(height: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(height))
}

private fun Context.rowSpacer() = View(this)
private fun Context.panel(color: Int, radius: Int) = GradientDrawable().apply {
    cornerRadius = dp(radius).toFloat()
    setColor(color)
}
