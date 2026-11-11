package com.roadstory.pleos.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.MainActivity
import com.roadstory.pleos.platform.DrivingRestriction
import com.roadstory.pleos.user.LibraryItem
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VehicleDrivingRestrictionDeviceTest {
    @Test
    fun restrictionClosesMenuAndRestoresSettingsWithSavedPreference() {
        val store = vehicleStore()
        val initialAutoplay = store.autoplay
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.describedView("SCR-HOME-001")
                    activity.describedView("설정").performClick()
                    activity.describedView("이야기 자동 재생").performClick()
                    assertEquals(!initialAutoplay, store.autoplay)
                    val parkedSwitch = activity.describedView("이야기 자동 재생")
                    val menuAnchor = activity.describedView("앱 메뉴 · 설정")
                    menuAnchor.performClick()
                    val popup = checkNotNull(activity.appMenu)
                    assertTrue(popup.isShowing)
                    val parkedLibraryItem = popup.contentView.descendants()
                        .single { it.contentDescription == "내 이야기" }

                    activity.applyDrivingRestriction(RESTRICTED)
                    assertFalse(popup.isShowing)
                    assertNull(activity.appMenu)
                    assertOnlyDrivingControls(activity.describedView(DRIVING_SCREEN))

                    // Keep detached Views to simulate callbacks queued before the restriction.
                    menuAnchor.performClick()
                    parkedLibraryItem.performClick()
                    parkedSwitch.performClick()
                    assertNull(activity.appMenu)
                    assertEquals(!initialAutoplay, store.autoplay)

                    activity.applyDrivingRestriction(PARKED)
                    activity.describedView("SCR-SETTINGS-001")
                    val restoredSwitch = activity.describedView("이야기 자동 재생") as PreferenceSwitch
                    assertEquals(!initialAutoplay, restoredSwitch.isChecked)
                }
            }
        } finally {
            store.autoplay = initialAutoplay
        }
    }

    @Test
    fun staleBrowsingCallbacksAreBlockedWhileCurrentStoryFeedbackWorks() {
        val store = vehicleStore()
        val originalStory = store.currentStory
        val originalCourseSaved = store.isCourseSaved(VehicleUserStore.DEFAULT_COURSE.id)
        val activeStory = LibraryItem("driving-active-${UUID.randomUUID()}", "현재 이야기 테스트")
        val libraryStory = LibraryItem("driving-library-${UUID.randomUUID()}", "보관한 이야기 테스트")
        store.toggleStory(libraryStory)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val courseId = VehicleUserStore.DEFAULT_COURSE.id
                val parkedCourse = awaitTagged(scenario, "focus:course:$courseId:open")
                lateinit var parkedRoute: View
                scenario.onActivity { activity ->
                    activity.describedView("SCR-HOME-001")
                    store.currentStory = activeStory
                    parkedRoute = activity.views().single {
                        it is Button && it.text.startsWith("이야기 편성 보기")
                    }
                    parkedCourse.performClick()
                }
                val parkedCourseSave = awaitTagged(scenario, "focus:course:$courseId:save")
                scenario.onActivity { activity ->
                    activity.describedView("뒤로").performClick()
                    activity.describedView("뒤로").performClick()
                    activity.describedView("앱 메뉴 · 홈").performClick()
                    checkNotNull(activity.appMenu).contentView.descendants()
                        .single { it.contentDescription == "내 이야기" }.performClick()
                    val parkedStorySave = activity.taggedView("focus:library:story:${libraryStory.id}:save")
                    val parkedStoryPlay = (parkedStorySave.parent as ViewGroup).descendants()
                        .single { it is Button && it.text == "듣기" }

                    activity.applyDrivingRestriction(RESTRICTED)
                    listOf(parkedRoute, parkedCourse, parkedCourseSave, parkedStoryPlay, parkedStorySave)
                        .forEach { it.performClick() }
                    assertOnlyDrivingControls(activity.describedView(DRIVING_SCREEN))
                    assertEquals(activeStory, store.currentStory)
                    assertTrue(store.isStorySaved(libraryStory.id))
                    assertEquals(originalCourseSaved, store.isCourseSaved(VehicleUserStore.DEFAULT_COURSE.id))

                    activity.taggedView("focus:player:save").performClick()
                    assertTrue(store.isStorySaved(activeStory.id))
                    activity.taggedView("focus:player:dislike").performClick()
                    assertTrue(store.isStoryDisliked(activeStory.id))
                    val selectedDislike = activity.taggedView("focus:player:dislike")
                    assertTrue(selectedDislike.isSelected)
                    selectedDislike.performClick()
                    assertTrue(store.isStoryDisliked(activeStory.id))

                    activity.applyDrivingRestriction(PARKED)
                    activity.describedView("SCR-SAVED-001")
                    activity.taggedView("focus:library:story:${libraryStory.id}:save")
                }
            }
        } finally {
            store.currentStory = originalStory
            if (store.isStorySaved(activeStory.id)) store.toggleStory(activeStory)
            if (store.isStorySaved(libraryStory.id)) store.toggleStory(libraryStory)
            store.setStoryDisliked(activeStory.id, false)
            if (store.isCourseSaved(VehicleUserStore.DEFAULT_COURSE.id) != originalCourseSaved) {
                store.toggleCourse(VehicleUserStore.DEFAULT_COURSE)
            }
        }
    }

    private fun awaitTagged(scenario: ActivityScenario<MainActivity>, tag: String): View {
        val deadline = SystemClock.elapsedRealtime() + 12_000
        do {
            var view: View? = null
            scenario.onActivity { activity -> view = activity.views().firstOrNull { it.tag == tag } }
            view?.let { return it }
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for $tag")
    }

    @Test
    fun everyRestrictedDestinationOffersTheSamePlaybackActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for ((widthDp, heightDp) in listOf(320 to 400, 1024 to 600)) {
                    val configuration = Configuration(activity.resources.configuration).apply {
                        screenWidthDp = widthDp
                        screenHeightDp = heightDp
                        fontScale = 1f
                    }
                    val context = activity.createConfigurationContext(configuration)
                    val expanded = widthDp >= 1000
                    val receivedActions = mutableListOf<String>()
                    val store = vehicleStore()
                    val renderer = RoadStoryScreens(
                        context = context,
                        navigate = { fail("Restricted screen tried to open $it") },
                        playbackAction = { receivedActions += it },
                        dislikeStory = { receivedActions += "dislike" },
                        openAppMenu = { fail("Restricted screen tried to open the menu") },
                        userStore = store,
                        refresh = {},
                        canUseParkedControls = { false },
                    )
                    for (screen in VehicleScreen.entries) {
                        val root = renderer.render(screen, RESTRICTED, expanded)
                        assertOnlyDrivingControls(root, canSkipNext = false)
                    }
                    for (state in listOf(PlaybackUiState(playing = true), PlaybackUiState(buffering = true))) {
                        val active = renderer.render(VehicleScreen.PLAYER, RESTRICTED, expanded, state.copy(
                            currentStoryId = store.currentStory.id, currentDurationSeconds = 60, listenedMs = 3_000L,
                        ))
                        assertEquals("일시정지", active.taggedView("focus:player:toggle").contentDescription)
                        assertControlsFit(active as ViewGroup, widthDp, heightDp)
                    }
                    val failed = renderer.render(
                        VehicleScreen.PLAYER, RESTRICTED, expanded, PlaybackUiState(failed = true, canSkipNext = true),
                    )
                    assertOnlyDrivingControls(failed, canSkipNext = true)
                    assertControlsFit(failed as ViewGroup, widthDp, heightDp)
                    for (action in ACTIONS) {
                        failed.taggedView("focus:player:$action").performClick()
                    }
                    assertEquals(ACTIONS, receivedActions)
                }
            }
        }
    }

    @Test
    fun parkedPlaybackUsesQueuePreviewAndSeparateFinishAndStopCommands() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val received = mutableListOf<String>()
                val dislikedStories = mutableListOf<LibraryItem>()
                val store = vehicleStore()
                val renderer = RoadStoryScreens(
                    context = activity,
                    navigate = { received += "screen:$it" },
                    playbackAction = { received += it },
                    dislikeStory = { dislikedStories += it },
                    openAppMenu = {},
                    userStore = store,
                    refresh = {},
                    canUseParkedControls = { true },
                )
                renderer.render(VehicleScreen.HOME, PARKED, expanded = false)
                    .taggedView("focus:home:start-nearby").performClick()

                val playing = PlaybackUiState(
                    playing = true,
                    canSkipNext = true,
                    nextTitle = "큐에서 받은 다음 이야기",
                    nextDurationSeconds = 72,
                )
                val player = renderer.render(VehicleScreen.PLAYER, PARKED, expanded = false, playback = playing)
                assertTrue(player.hasText("큐에서 받은 다음 이야기"))
                assertTrue(player.hasText("약 1:12"))
                val shownStory = store.currentStory
                player.taggedView("focus:player:dislike").performClick()
                assertEquals(listOf(shownStory), dislikedStories)
                player.taggedView("focus:player:next").performClick()
                player.taggedView("focus:player:finish-after-current").performClick()
                player.taggedView("focus:player:stop").performClick()
                assertEquals(listOf("start-nearby", "screen:PLAYER", "next", "finish-after-current", "stop"), received)

                val scheduled = renderer.render(
                    VehicleScreen.PLAYER, PARKED, expanded = false, playback = playing.copy(endAfterCurrent = true),
                )
                assertTrue(scheduled.hasText("현재 이야기 후 자동 재생을 종료합니다."))
                assertFalse(scheduled.taggedView("focus:player:finish-after-current").isEnabled)
                assertTrue(scheduled.taggedView("focus:player:stop").isEnabled)

                val tail = renderer.render(VehicleScreen.PLAYER, PARKED, expanded = false)
                assertTrue(tail.hasText("다음 이야기가 없어요."))
                assertFalse(tail.taggedView("focus:player:next").performClick())
                val empty = renderer.render(
                    VehicleScreen.PLAYER, PARKED, expanded = false, playback = PlaybackUiState(queueEmpty = true),
                )
                assertTrue(empty.hasText("이 지역의 이야기는 아직 준비 중입니다."))

                val filtered = renderer.render(
                    VehicleScreen.PLAYER, PARKED, expanded = false,
                    playback = PlaybackUiState(queueEmpty = true, filteredByPreferences = true),
                )
                assertTrue(filtered.hasText("현재 설정에 맞는 이야기가 없어요."))
                assertFalse(filtered.hasText("이 지역의 이야기는 아직 준비 중입니다."))
                filtered.descendants().filterIsInstance<Button>().single { it.text == "관심 주제 설정" }.performClick()
                assertEquals("screen:SETTINGS", received.last())

                val restricted = renderer.render(
                    VehicleScreen.PLAYER, RESTRICTED, expanded = false,
                    playback = PlaybackUiState(queueEmpty = true, filteredByPreferences = true),
                )
                assertOnlyDrivingControls(restricted, canSkipNext = false)
                assertFalse(restricted.hasText("관심 주제 설정"))
            }
        }
    }

    private fun assertControlsFit(root: ViewGroup, widthDp: Int, heightDp: Int) {
        val width = root.context.dp(widthDp)
        val height = root.context.dp(heightDp)
        val minimumTouchSize = root.context.dp(VehicleUiPolicy.primaryActionMinHeightDp)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        val viewport = Rect(0, 0, width, height)
        for (action in ACTIONS) {
            val control = root.taggedView("focus:player:$action")
            val bounds = Rect(0, 0, control.width, control.height)
            root.offsetDescendantRectToMyCoords(control, bounds)
            assertTrue("$action touch target is too small", bounds.width() >= minimumTouchSize && bounds.height() >= minimumTouchSize)
            assertTrue("$action is clipped at ${widthDp}dp", viewport.contains(bounds))
        }
    }

    private fun assertOnlyDrivingControls(root: View, canSkipNext: Boolean? = null) {
        assertEquals(DRIVING_SCREEN, root.contentDescription)
        val views = root.descendants().toList()
        val tags: Set<Any> = ACTIONS.map { "focus:player:$it" }.toSet()
        val controls = views.filter { it.tag in tags }
        assertEquals(ACTIONS.size, controls.size)
        assertEquals(tags, controls.map { it.tag }.toSet())
        assertTrue(views.filter { it.isClickable || it.isLongClickable || it.isFocusable }.all { it.tag in tags })
        controls.forEach { control ->
            val enabled = if (control.tag == "focus:player:next") canSkipNext ?: control.isEnabled else true
            assertEquals(enabled, control.isEnabled)
            assertEquals(enabled, control.isFocusable)
            assertEquals(View.VISIBLE, control.visibility)
        }
        assertFalse(views.any { it is ImageView || it is ScrollView || it is SeekBar })
        assertFalse(views.any {
            val description = it.contentDescription?.toString()
            description in PARKED_CONTROLS || description?.startsWith("앱 메뉴 ·") == true
        })
    }

    private fun vehicleStore() = VehicleUserStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private fun MainActivity.describedView(description: String) = views().single { it.contentDescription == description }
    private fun MainActivity.taggedView(tag: String) = window.decorView.taggedView(tag)
    private fun MainActivity.views() = window.decorView.descendants()
    private fun View.taggedView(tag: String) = descendants().single { it.tag == tag }
    private fun View.hasText(text: String) = descendants().any { it is TextView && it.text == text }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }

    private companion object {
        val PARKED = DrivingRestriction(false, 0)
        val RESTRICTED = DrivingRestriction(true, 0)
        val ACTIONS = listOf("toggle", "next", "save", "dislike", "stop")
        val PARKED_CONTROLS = setOf("뒤로", "설정", "이전 이야기", "현재 이야기 상세")
        const val DRIVING_SCREEN = "SCR-DRIVE-LIMIT-001"
    }
}
