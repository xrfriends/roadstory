package com.roadstory.pleos.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.roadstory.pleos.platform.NavigationGateway
import com.roadstory.pleos.platform.NavigationSession
import com.roadstory.pleos.platform.VehicleNavigationState
import com.roadstory.pleos.playback.CourseDetail
import com.roadstory.pleos.playback.CourseSummary
import com.roadstory.pleos.playback.DestinationMode
import com.roadstory.pleos.playback.DestinationPlan
import com.roadstory.pleos.playback.StoryApiClient

sealed interface CatalogState<out T> {
    data object Idle : CatalogState<Nothing>
    data object Loading : CatalogState<Nothing>
    data object Failed : CatalogState<Nothing>
    data class Ready<T>(val data: T) : CatalogState<T>
}

data class JourneyUiState(
    val navigation: VehicleNavigationState = VehicleNavigationState.Unavailable,
    val destinationMode: DestinationMode = DestinationMode.STANDARD,
    val destinationPlan: CatalogState<DestinationPlan> = CatalogState.Idle,
    val courses: CatalogState<List<CourseSummary>> = CatalogState.Idle,
    val course: CatalogState<CourseDetail> = CatalogState.Idle,
)

sealed interface JourneyAction {
    data object RefreshDestination : JourneyAction
    data object RefreshCourses : JourneyAction
    data class SetDestinationMode(val mode: DestinationMode) : JourneyAction
    data class OpenCourse(val courseId: String) : JourneyAction
    data class PlayDestination(val mode: DestinationMode, val storyId: String? = null) : JourneyAction
    data class PlayCourse(val courseId: String, val storyId: String? = null) : JourneyAction
    data class GuideFirstStop(val courseId: String) : JourneyAction
}

/** Loads only data needed by the current parked screen. Late responses cannot replace a new selection. */
internal class JourneyScreensController(
    private val context: Context,
    private val onChanged: () -> Unit,
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val api = StoryApiClient()
    private var active = false
    private var closed = false
    private var navigation: NavigationGateway? = null
    private var navigationObserver: AutoCloseable? = null
    private var screen = VehicleScreen.HOME
    private var planGeneration = 0L
    private var courseGeneration = 0L
    var selectedCourseId: String? = null
        private set
    var state = JourneyUiState()
        private set

    fun start() {
        if (active || closed) return
        active = true
        navigation = NavigationSession.acquire(context)
        navigationObserver = navigation?.observe(::onNavigation)
        navigation?.refresh()
        prepare()
    }

    fun stop() {
        active = false
        navigationObserver?.close()
        navigationObserver = null
        navigation?.close()
        navigation = null
        planGeneration++
        state = state.copy(
            navigation = VehicleNavigationState.Unavailable,
            destinationPlan = CatalogState.Idle,
        )
    }

    fun show(screen: VehicleScreen, courseId: String? = selectedCourseId) {
        this.screen = screen
        if (screen == VehicleScreen.COURSE_DETAIL && courseId != selectedCourseId) {
            selectedCourseId = courseId
            courseGeneration++
            state = state.copy(course = CatalogState.Idle)
        }
        prepare()
    }

    private fun prepare() {
        if (!active) return
        when (screen) {
            VehicleScreen.HOME, VehicleScreen.COURSES -> if (state.courses == CatalogState.Idle) loadCourses()
            VehicleScreen.COURSE_DETAIL -> if (state.course == CatalogState.Idle) loadCourse()
            VehicleScreen.DESTINATION -> if (state.destinationPlan == CatalogState.Idle) loadDestination()
            else -> Unit
        }
    }

    fun refreshDestination() {
        navigation?.refresh()
        loadDestination()
    }

    fun changeMode(mode: DestinationMode) {
        if (mode == state.destinationMode) return
        state = state.copy(destinationMode = mode)
        loadDestination()
    }

    fun loadCourses() {
        if (!active || state.courses == CatalogState.Loading) return
        update(state.copy(courses = CatalogState.Loading))
        api.loadCourses { result ->
            main.post { update(state.copy(courses = result.toCatalogState())) }
        }
    }

    fun loadCourse() {
        val id = selectedCourseId ?: return
        if (!active || state.course == CatalogState.Loading) return
        val generation = ++courseGeneration
        update(state.copy(course = CatalogState.Loading))
        api.loadCourse(id) { result ->
            main.post {
                if (generation == courseGeneration) {
                    update(state.copy(course = result.mapCatching { check(it.summary.id == id); it }.toCatalogState()))
                }
            }
        }
    }

    fun guideFirstStop(courseId: String, callback: (Boolean) -> Unit) {
        val course = (state.course as? CatalogState.Ready)?.data?.takeIf { it.summary.id == courseId }
        val stop = course?.stops?.firstOrNull()
        val gateway = navigation
        if (!active || stop == null || gateway == null) {
            callback(false)
            return
        }
        // The navigation window may take foreground before the route result arrives.
        val requestLease = NavigationSession.acquire(context)
        requestLease.requestFirstStop(stop.latitude, stop.longitude, stop.placeName) { confirmed ->
            try {
                if (!closed) callback(confirmed)
            } finally {
                requestLease.close()
            }
        }
    }

    private fun onNavigation(updated: VehicleNavigationState) {
        if (!active || updated == state.navigation) return
        val previous = state.navigation as? VehicleNavigationState.Guiding
        val next = updated as? VehicleNavigationState.Guiding
        val planInvalid = previous?.routeRevision != next?.routeRevision ||
            previous?.destination?.latitude != next?.destination?.latitude ||
            previous?.destination?.longitude != next?.destination?.longitude ||
            etaBucket(previous) != etaBucket(next)
        if (planInvalid) planGeneration++
        val value = state.copy(navigation = updated, destinationPlan = if (planInvalid) CatalogState.Idle else state.destinationPlan)
        if (!planInvalid && previous != null && next != null && previous.destination.name == next.destination.name &&
            previous.destination.remainingDurationSeconds?.let { (it + 59) / 60 } ==
            next.destination.remainingDurationSeconds?.let { (it + 59) / 60 }) {
            // Keep the exact ETA for planning without replacing visible views for a seconds-only change.
            state = value
        } else {
            update(value)
        }
        if (screen == VehicleScreen.DESTINATION && planInvalid) loadDestination()
    }

    private fun etaBucket(guidance: VehicleNavigationState.Guiding?) =
        (guidance?.destination?.remainingDurationSeconds ?: 0) / 60

    private fun loadDestination() {
        val destination = (state.navigation as? VehicleNavigationState.Guiding)?.destination
        val generation = ++planGeneration
        if (!active || destination == null || (destination.remainingDurationSeconds ?: 0) < 60) {
            update(state.copy(destinationPlan = CatalogState.Idle))
            return
        }
        update(state.copy(destinationPlan = CatalogState.Loading))
        api.loadDestination(destination, state.destinationMode) { result ->
            main.post {
                if (generation == planGeneration) update(state.copy(destinationPlan = result.toCatalogState()))
            }
        }
    }

    private fun update(value: JourneyUiState) {
        if (closed || value == state) return
        state = value
        if (active) onChanged()
    }

    private fun <T> Result<T>.toCatalogState(): CatalogState<T> = fold({ CatalogState.Ready(it) }, { CatalogState.Failed })

    override fun close() {
        stop()
        closed = true
        api.close()
        main.removeCallbacksAndMessages(null)
    }
}
