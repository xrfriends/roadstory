package com.roadstory.pleos.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.platform.DrivingRestriction
import com.roadstory.pleos.platform.NavigationDestination
import com.roadstory.pleos.platform.VehicleNavigationState
import com.roadstory.pleos.playback.DestinationMode
import com.roadstory.pleos.playback.DestinationPlan
import com.roadstory.pleos.playback.PlaylistBasis
import com.roadstory.pleos.playback.QueueStory
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DestinationRendererDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun activeDestinationShowsEtaAndUsesTheRenderedModeAndStory() {
        instrumentation.runOnMainSync {
            val actions = mutableListOf<JourneyAction>()
            val story = QueueStory("story-test", "성수 이야기", "성수동", 45)
            val destination = NavigationDestination("성수역", 37.544628, 127.055983, 391)
            val state = JourneyUiState(
                navigation = VehicleNavigationState.Guiding(destination, 1),
                destinationMode = DestinationMode.FAMILY,
                destinationPlan = CatalogState.Ready(DestinationPlan(PlaylistBasis.DESTINATION_NEARBY, 45, listOf(story))),
            )
            val screen = render(state, actions)
            val views = screen.descendants().toList()
            val labels = views.filterIsInstance<TextView>().map { it.text.toString() }
            assertEquals("SCR-DEST-001", screen.contentDescription)
            assertTrue("Destination must come from guidance", "성수역" in labels)
            assertTrue("ETA must come from guidance", "내비게이션 남은 시간 약 7분" in labels)
            assertTrue("Plan should show its selection basis", "목적지 주변 이야기" in labels)
            assertTrue("Catalog story should be visible", story.title in labels)

            views.single { it.contentDescription == "${story.title} 듣기" }.performClick()
            views.filterIsInstance<Button>().single { it.text == "▶  전체 재생" }.performClick()
            views.filterIsInstance<Button>().single { it.text == DestinationMode.SHORT.label }.performClick()
            assertEquals(listOf(
                JourneyAction.PlayDestination(DestinationMode.FAMILY, story.id),
                JourneyAction.PlayDestination(DestinationMode.FAMILY),
                JourneyAction.SetDestinationMode(DestinationMode.SHORT),
            ), actions)
        }
    }

    @Test
    fun noDestinationHidesAnObsoletePlanAndOffersRefresh() {
        instrumentation.runOnMainSync {
            val actions = mutableListOf<JourneyAction>()
            val oldPlan = DestinationPlan(PlaylistBasis.DESTINATION_NEARBY, 60, listOf(QueueStory("old-id", "이전 편성")))
            val state = JourneyUiState(
                navigation = VehicleNavigationState.NoDestination,
                destinationPlan = CatalogState.Ready(oldPlan),
            )
            val views = render(state, actions).descendants().toList()
            val labels = views.filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("내비게이션에서 목적지 안내를 시작해 주세요." in labels)
            assertFalse("이전 편성" in labels)
            assertFalse(labels.any { it.contains("전체 재생") || it == DestinationMode.STANDARD.label })
            views.filterIsInstance<Button>().single { it.text == "내비 상태 다시 확인" }.performClick()
            assertEquals(listOf(JourneyAction.RefreshDestination), actions)
        }
    }

    private fun render(state: JourneyUiState, actions: MutableList<JourneyAction>): View {
        val context = instrumentation.targetContext
        return RoadStoryScreens(
            context = context,
            navigate = {},
            playbackAction = {},
            dislikeStory = {},
            openAppMenu = {},
            userStore = VehicleUserStore(context),
            refresh = {},
            canUseParkedControls = { true },
            journeyAction = { actions += it },
        ).render(VehicleScreen.DESTINATION, DrivingRestriction(false, 0), expanded = false, journey = state)
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (i in 0 until childCount) yieldAll(getChildAt(i).descendants())
        }
    }
}
