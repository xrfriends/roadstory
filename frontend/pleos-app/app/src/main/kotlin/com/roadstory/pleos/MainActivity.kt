package com.roadstory.pleos

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.Toast
import com.roadstory.pleos.ui.VehicleAppMenu
import com.roadstory.pleos.ui.dp
import com.roadstory.pleos.ui.appMenuRow
import com.roadstory.pleos.ui.surface
import com.roadstory.pleos.ui.RoadStoryIcon
import com.roadstory.pleos.user.LibraryItem
import com.roadstory.pleos.user.LibrarySyncMode
import com.roadstory.pleos.user.VehicleLibrarySync
import com.roadstory.pleos.user.VehicleUserStore
import com.roadstory.pleos.platform.CarUxRestrictionSource
import com.roadstory.pleos.platform.DrivingRestriction
import com.roadstory.pleos.platform.DrivingRestrictionSource
import com.roadstory.pleos.platform.FailClosedDrivingRestrictionSource
import com.roadstory.pleos.platform.VehicleLocationSourceFactory
import com.roadstory.pleos.platform.VehicleNavigationState
import com.roadstory.pleos.playback.PlaybackIssue
import com.roadstory.pleos.playback.PlaybackSource
import com.roadstory.pleos.playback.PlaybackSessionContract
import com.roadstory.pleos.playback.RoadStoryPlaybackService
import com.roadstory.pleos.ui.RoadStoryPalette
import com.roadstory.pleos.ui.RoadStoryScreens
import com.roadstory.pleos.ui.VehicleScreen
import com.roadstory.pleos.ui.PlaybackUiState
import com.roadstory.pleos.ui.VehicleUiPolicy
import com.roadstory.pleos.ui.CatalogState
import com.roadstory.pleos.ui.JourneyAction
import com.roadstory.pleos.ui.JourneyScreensController

class MainActivity : Activity() {
    private lateinit var restrictions: DrivingRestrictionSource
    private val location by lazy { VehicleLocationSourceFactory.create(this) }
    private var restriction = DrivingRestriction.RESTRICTED
    private var screen = VehicleScreen.HOME
    private var controller: MediaController? = null
    private lateinit var browser: MediaBrowser
    private lateinit var screens: RoadStoryScreens
    private lateinit var journey: JourneyScreensController
    private var guidanceConfirmation: AlertDialog? = null
    private var libraryDialog: AlertDialog? = null
    private var requestingLocationPermission = false
    private var showLocationPermission = false
    private val userStore by lazy { VehicleUserStore(this) }
    private val librarySync by lazy { VehicleLibrarySync.get(this) }
    private var userObservation: AutoCloseable? = null
    private var libraryObservation: AutoCloseable? = null
    private var playbackUiState = PlaybackUiState()
    private var pendingPlaybackAction: String? = null
    private var pendingAudioDownload: String? = null
    private data class CatalogPlaybackRequest(val action: String, val extras: Bundle)
    private var pendingCatalogPlayback: CatalogPlaybackRequest? = null
    private val renderHandler = Handler(Looper.getMainLooper())
    private val renderFrame = Runnable {
        if (!isFinishing && !isDestroyed && ::screens.isInitialized) render()
    }
    private val mediaCallback = object : MediaController.Callback() {
        override fun onSessionEvent(event: String, extras: Bundle?) {
            if (event != PlaybackSessionContract.DOWNLOAD_AUDIO_RESULT) return
            val id = extras?.getString(PlaybackSessionContract.SELECTED_STORY_ID) ?: return
            if (pendingAudioDownload != id) return
            pendingAudioDownload = null
            requestRender()
            if (screen == VehicleScreen.PLAYER && canUseParkedControls()) {
                val success = extras.getBoolean(PlaybackSessionContract.DOWNLOAD_SUCCEEDED)
                Toast.makeText(this@MainActivity, if (success) "차량에 음원을 저장했어요." else "음원을 저장하지 못했어요.",
                    Toast.LENGTH_SHORT).show()
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val extras = state?.extras
            val currentId = extras?.getString(PlaybackSessionContract.CURRENT_ID)
            val currentTitle = extras?.getString(PlaybackSessionContract.CURRENT_TITLE)
            val updated = PlaybackUiState(
                playing = state?.state == PlaybackState.STATE_PLAYING,
                buffering = state?.state == PlaybackState.STATE_BUFFERING,
                failed = state?.state == PlaybackState.STATE_ERROR,
                canSkipNext = ((state?.actions ?: 0L) and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
                nextTitle = extras?.getString(PlaybackSessionContract.NEXT_TITLE),
                nextDurationSeconds = extras?.getInt(PlaybackSessionContract.NEXT_DURATION_SECONDS) ?: 0,
                currentStoryId = currentId,
                currentDurationSeconds = extras?.getInt(PlaybackSessionContract.CURRENT_DURATION_SECONDS) ?: 0,
                endAfterCurrent = extras?.getBoolean(PlaybackSessionContract.END_AFTER_CURRENT) == true,
                autoplayEnabled = extras?.getBoolean(PlaybackSessionContract.AUTOPLAY_ENABLED, userStore.autoplay) ?: userStore.autoplay,
                queueEmpty = extras?.getBoolean(PlaybackSessionContract.QUEUE_EMPTY) == true,
                filteredByPreferences = extras?.getBoolean(PlaybackSessionContract.FILTERED_BY_PREFERENCES) == true,
                source = PlaybackSource.entries.find { it.name == extras?.getString(PlaybackSessionContract.SOURCE) },
                issue = PlaybackIssue.entries.find { it.name == extras?.getString(PlaybackSessionContract.ISSUE) },
                errorMessage = state?.errorMessage?.toString(),
                listenedMs = extras?.getLong(PlaybackSessionContract.LISTENED_MS) ?: 0L,
                listeningSinceElapsedMs = extras?.getLong(PlaybackSessionContract.LISTENING_SINCE_ELAPSED_MS, -1L)
                    ?.takeIf { it >= 0L },
                resumeAtSentence = extras?.getBoolean(PlaybackSessionContract.RESUME_AT_SENTENCE) == true,
                audioPositionMs = extras?.getLong(PlaybackSessionContract.AUDIO_POSITION_MS, -1L)?.takeIf { it >= 0L },
                audioDurationMs = extras?.getLong(PlaybackSessionContract.AUDIO_DURATION_MS, -1L)?.takeIf { it > 0L },
                audioPositionUpdatedAtMs = extras?.getLong(PlaybackSessionContract.AUDIO_POSITION_UPDATED_AT_MS, -1L)
                    ?.takeIf { it >= 0L },
                offlineAudio = extras?.getBoolean(PlaybackSessionContract.OFFLINE_AUDIO) == true,
                currentStory = if (currentId != null && !currentTitle.isNullOrBlank()) {
                    LibraryItem(currentId, currentTitle, extras.getString(PlaybackSessionContract.CURRENT_PLACE).orEmpty(),
                        extras.getString(PlaybackSessionContract.CURRENT_CATEGORY))
                } else null,
            )
            if (updated != playbackUiState) {
                playbackUiState = updated
                requestRender()
            }
        }
    }
    internal var appMenu: PopupWindow? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = savedInstanceState?.getString("screen")?.let { name -> runCatching { VehicleScreen.valueOf(name) }.getOrNull() } ?: VehicleScreen.HOME
        window.navigationBarColor = RoadStoryPalette.background
        restrictions = createRestrictionSource()
        journey = JourneyScreensController(this, ::requestRender)
        journey.show(screen, savedInstanceState?.getString("courseId"))
        screens = RoadStoryScreens(
            this, ::navigate, ::playbackAction, ::dislikeStory, ::showAppMenu, userStore, ::render, ::canUseParkedControls,
            ::journeyAction, ::showLibrarySignIn, ::syncLibrary, ::signOutOfLibrary, ::showGuestBookmarkImport,
            activeLibraryAccount = { librarySync.status.accountId },
        )
        render()
        userObservation = userStore.observe(::requestRender)
        var libraryOwner = librarySync.status.accountId
        libraryObservation = librarySync.observe { status ->
            if (status.accountId == libraryOwner) {
                requestRender()
            } else {
                libraryOwner = status.accountId
                pendingCatalogPlayback = null
                pendingPlaybackAction = null
                pendingAudioDownload = null
                libraryDialog?.dismiss()
                val currentCanFinish = playbackUiState.playing ||
                    (playbackUiState.buffering && playbackUiState.currentStory != null && playbackUiState.listenedMs > 0L)
                playbackUiState = if (status.mode == LibrarySyncMode.SIGN_IN_REQUIRED && currentCanFinish) {
                    playbackUiState.copy(source = null, endAfterCurrent = true, canSkipNext = false,
                        nextTitle = null, nextDurationSeconds = 0)
                } else PlaybackUiState()
                if (!isFinishing && !isDestroyed) render()
            }
        }
        connectMediaBrowser()
        restrictions.observe(::applyDrivingRestriction)
    }

    private fun createRestrictionSource(): DrivingRestrictionSource =
        FailClosedDrivingRestrictionSource { CarUxRestrictionSource(this) }

    internal fun applyDrivingRestriction(updated: DrivingRestriction) {
        if (restriction == updated || isFinishing || isDestroyed) return
        restriction = updated
        if (updated.blocksDetailedContent) {
            pendingPlaybackAction = pendingPlaybackAction?.takeIf(VehicleUiPolicy::isDrivingAction)
            pendingCatalogPlayback = null
            guidanceConfirmation?.dismiss()
            libraryDialog?.dismiss()
        }
        journey.show(VehicleUiPolicy.permittedScreen(screen, restriction))
        render()
    }

    private fun canUseParkedControls(): Boolean {
        if (isFinishing || isDestroyed || restriction.blocksDetailedContent) return false
        // A click queued just before the platform event must also pass the live check.
        val current = restrictions.current()
        if (!current.blocksDetailedContent) return true
        applyDrivingRestriction(current)
        return false
    }

    private fun navigate(target: VehicleScreen) {
        showScreen(target)
    }

    private fun showScreen(target: VehicleScreen, courseId: String? = journey.selectedCourseId) {
        if (!canUseParkedControls()) return
        screen = target
        if (target == VehicleScreen.COLLECTION) librarySync.syncNow()
        if (target != VehicleScreen.PLAYER) {
            showLocationPermission = false
            pendingCatalogPlayback = null
        }
        journey.show(target, courseId)
        render()
    }

    private fun requestRender() {
        if (!isFinishing && !isDestroyed && !renderHandler.hasCallbacks(renderFrame)) {
            // Playback state and local history can change in the same frame.
            renderHandler.postDelayed(renderFrame, 16L)
        }
    }

    private fun render() {
        renderHandler.removeCallbacks(renderFrame)
        val focusedView = window.decorView.findFocus()
        val previousFocus = focusedView?.contentDescription
        val previousFocusTag = focusedView?.tag as? String
        appMenu?.dismiss()
        val expanded = resources.configuration.screenWidthDp >= 1000
        val displayedPlayback = if (showLocationPermission) {
            playbackUiState.copy(issue = PlaybackIssue.LOCATION_PERMISSION)
        } else playbackUiState.copy(downloadingAudio = pendingAudioDownload != null)
        val content = screens.render(
            screen, restriction, expanded, displayedPlayback, journey.state,
            librarySync.status, librarySync.developmentSignInAvailable,
            screen == VehicleScreen.COLLECTION && librarySync.status.accountId != null &&
                (librarySync.guestSavedStories.any { !userStore.isStorySaved(it.id) } ||
                    librarySync.guestSavedCourses.any { !userStore.isCourseSaved(it.id) }),
        )
        setContentView(content)
        fun restoreFocus(view: View): Boolean {
            val sameControl = if (previousFocusTag != null) view.tag == previousFocusTag
                else previousFocus != null && view.contentDescription == previousFocus
            if (sameControl && view.isFocusable) return view.requestFocus()
            if (view is ViewGroup) for (index in 0 until view.childCount) if (restoreFocus(view.getChildAt(index))) return true
            return false
        }
        if (!restoreFocus(content)) content.findFocus() ?: content.focusSearch(View.FOCUS_DOWN)?.requestFocus()
    }

    private fun showAppMenu(anchor: View) {
        if (!canUseParkedControls() || !anchor.isAttachedToWindow) return
        val entries = VehicleAppMenu.availableEntries(restriction)
        if (entries.isEmpty()) return
        if (appMenu?.isShowing == true) {
            appMenu?.dismiss()
            return
        }
        val menuContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = surface(RoadStoryPalette.raisedSurface, 24, RoadStoryPalette.line)
            clipToOutline = true
            accessibilityPaneTitle = "차량 앱 메뉴"
        }
        val popup = PopupWindow(
            menuContent,
            minOf(dp(500), (anchor.rootView.width - dp(32)).coerceAtLeast(dp(240))),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(surface(RoadStoryPalette.raisedSurface, 24, RoadStoryPalette.line))
            isOutsideTouchable = true
            elevation = dp(16).toFloat()
        }
        entries.forEach { entry ->
            menuContent.addView(appMenuRow(entry, entry.screen == screen) {
                popup.dismiss()
                // Recheck platform state even if it changed while the menu was open.
                if (entry in VehicleAppMenu.availableEntries(restriction)) navigate(entry.screen)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)))
        }
        popup.setOnDismissListener {
            anchor.isSelected = false
            anchor.stateDescription = "접힘"
            anchor.rootView.findViewWithTag<RoadStoryIcon>("menuChevron")?.rotation = 0f
            if (anchor.isAttachedToWindow) anchor.requestFocus()
            appMenu = null
        }
        appMenu = popup
        anchor.isSelected = true
        anchor.stateDescription = "펼쳐짐"
        anchor.rootView.findViewWithTag<RoadStoryIcon>("menuChevron")?.rotation = 180f
        val anchorLocation = IntArray(2).also(anchor::getLocationInWindow)
        val rootLocation = IntArray(2).also(anchor.rootView::getLocationInWindow)
        popup.showAsDropDown(anchor, rootLocation[0] + dp(18) - anchorLocation[0], dp(20))
    }

    private fun connectMediaBrowser() {
        browser = MediaBrowser(this, ComponentName(this, RoadStoryPlaybackService::class.java), object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                if (isFinishing || isDestroyed) return
                controller?.unregisterCallback(mediaCallback)
                controller = MediaController(this@MainActivity, browser.sessionToken).apply { registerCallback(mediaCallback) }
                mediaCallback.onPlaybackStateChanged(controller?.playbackState)
                val pending = pendingPlaybackAction
                pendingPlaybackAction = null
                pending?.let(::playbackAction)
                val catalog = pendingCatalogPlayback
                pendingCatalogPlayback = null
                if (catalog != null && screen == VehicleScreen.PLAYER && canUseParkedControls()) {
                    controller?.transportControls?.sendCustomAction(catalog.action, catalog.extras)
                }
            }

            override fun onConnectionSuspended() {
                controller?.unregisterCallback(mediaCallback)
                controller = null
                pendingAudioDownload = null
                mediaCallback.onPlaybackStateChanged(null)
            }

            override fun onConnectionFailed() {
                pendingPlaybackAction = null
                pendingCatalogPlayback = null
            }
        }, null)
        browser.connect()
    }

    private fun showLibrarySignIn() {
        if (!canUseParkedControls() || screen != VehicleScreen.COLLECTION ||
            !librarySync.developmentSignInAvailable || librarySync.status.mode == LibrarySyncMode.SIGNING_IN) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            filters = arrayOf(InputFilter.LengthFilter(128))
            isSaveEnabled = false
            setSingleLine(true)
            hint = "개발용 토큰"
        }
        libraryDialog?.dismiss()
        libraryDialog = AlertDialog.Builder(this)
            .setTitle("개발 계정 연결")
            .setView(input)
            .setNegativeButton("취소", null)
            .setPositiveButton("연결") { _, _ ->
                if (canUseParkedControls() && screen == VehicleScreen.COLLECTION) {
                    val token = input.text.toString().trim()
                    if (token.isNotEmpty()) librarySync.signInForDevelopment(token)
                }
            }
            .create().apply {
                setOnDismissListener { input.text.clear(); libraryDialog = null }
                show()
            }
    }

    private fun syncLibrary() {
        if (canUseParkedControls() && screen == VehicleScreen.COLLECTION) librarySync.syncNow()
    }

    private fun signOutOfLibrary() {
        if (canUseParkedControls() && screen == VehicleScreen.COLLECTION) librarySync.signOut()
    }

    private fun showGuestBookmarkImport() {
        if (!canUseParkedControls() || screen != VehicleScreen.COLLECTION) return
        val account = librarySync.status.accountId ?: return
        val stories = librarySync.guestSavedStories.filterNot { userStore.isStorySaved(it.id) }
        val courses = librarySync.guestSavedCourses.filterNot { userStore.isCourseSaved(it.id) }
        val labels = (stories.map { "이야기 · ${it.title}" } + courses.map { "코스 · ${it.title}" }).toTypedArray()
        if (labels.isEmpty()) return
        val selected = BooleanArray(labels.size)
        libraryDialog?.dismiss()
        libraryDialog = AlertDialog.Builder(this)
            .setTitle("계정에 복사할 저장 항목")
            .setMultiChoiceItems(labels, selected) { _, index, checked -> selected[index] = checked }
            .setNegativeButton("취소", null)
            .setPositiveButton("가져오기") { _, _ ->
                if (canUseParkedControls() && screen == VehicleScreen.COLLECTION) {
                    val storyIds = stories.filterIndexed { index, _ -> selected[index] }.mapTo(mutableSetOf(), LibraryItem::id)
                    val courseIds = courses.filterIndexed { index, _ -> selected[stories.size + index] }
                        .mapTo(mutableSetOf(), LibraryItem::id)
                    librarySync.importGuestBookmarks(account, storyIds, courseIds)
                }
            }
            .create().apply {
                setOnDismissListener { libraryDialog = null }
                show()
            }
    }

    private fun dislikeStory(story: LibraryItem) {
        if (!isFinishing && !isDestroyed) {
            userStore.setStoryDisliked(story, disliked = true)
            render()
        }
    }

    private fun playbackAction(action: String) {
        if (isFinishing || isDestroyed || (!VehicleUiPolicy.isDrivingAction(action) && !canUseParkedControls())) return
        if (action == "location-settings") {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(this, "앱 권한 설정을 열 수 없어요.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (action == "start-nearby") {
            val missing = location.missingPermissions()
            if (missing.isNotEmpty()) {
                showLocationPermission = true
                if (!requestingLocationPermission) {
                    requestingLocationPermission = true
                    requestPermissions(missing.toTypedArray(), LOCATION_PERMISSION_REQUEST)
                }
                return
            }
            showLocationPermission = false
        } else if (action.startsWith("play:") || action == "stop") {
            showLocationPermission = false
        }
        if (action == "stop") pendingCatalogPlayback = null
        if (action == "save") {
            userStore.toggleStory(playbackUiState.displayedStory(userStore.currentStory))
            render()
            return
        }
        val controls = controller?.transportControls ?: run {
            // The first playback request can arrive before the media service connects.
            if (action == "stop") pendingPlaybackAction = null
            else if (action == "start-nearby" || action == "play" || action == "toggle" || action.startsWith("play:")) {
                pendingPlaybackAction = action
            }
            return
        }
        if (action.startsWith("play:")) {
            controls.playFromMediaId(action.removePrefix("play:"), null)
            return
        }
        when (action) {
            "start-nearby" -> controls.sendCustomAction(PlaybackSessionContract.START_NEARBY, null)
            "finish-after-current" -> controls.sendCustomAction(PlaybackSessionContract.FINISH_AFTER_CURRENT, null)
            "download-audio" -> if (screen == VehicleScreen.PLAYER && pendingAudioDownload == null) {
                val id = playbackUiState.displayedStory(userStore.currentStory).id
                pendingAudioDownload = id
                controls.sendCustomAction(PlaybackSessionContract.DOWNLOAD_AUDIO, Bundle().apply {
                    putString(PlaybackSessionContract.SELECTED_STORY_ID, id)
                })
                render()
            }
            "play" -> controls.play()
            "toggle" -> when (controller?.playbackState?.state) {
                PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> controls.pause()
                else -> controls.play()
            }
            "next" -> controls.skipToNext()
            "stop" -> controls.stop()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCATION_PERMISSION_REQUEST) return
        val wasRequested = requestingLocationPermission
        requestingLocationPermission = false
        if (!wasRequested || screen != VehicleScreen.PLAYER || !canUseParkedControls()) return
        showLocationPermission = location.missingPermissions().isNotEmpty()
        if (!showLocationPermission) playbackAction("start-nearby")
        render()
    }

    private fun journeyAction(action: JourneyAction) {
        if (!canUseParkedControls()) return
        when (action) {
            JourneyAction.RefreshCourses -> journey.loadCourses()
            JourneyAction.RefreshDestination -> journey.refreshDestination()
            is JourneyAction.SetDestinationMode -> journey.changeMode(action.mode)
            is JourneyAction.OpenCourse -> {
                showScreen(VehicleScreen.COURSE_DETAIL, action.courseId)
                if (journey.state.course == CatalogState.Failed) journey.loadCourse()
            }
            is JourneyAction.PlayDestination -> {
                val plan = (journey.state.destinationPlan as? CatalogState.Ready)?.data ?: return
                val guiding = journey.state.navigation as? VehicleNavigationState.Guiding ?: return
                if ((guiding.destination.remainingDurationSeconds ?: 0) < 60 || plan.stories.isEmpty() ||
                    action.mode != journey.state.destinationMode || screen != VehicleScreen.DESTINATION ||
                    (action.storyId != null && plan.stories.none { it.id == action.storyId })) return
                sendCatalogPlayback(PlaybackSessionContract.START_DESTINATION, action.storyId) {
                    putString(PlaybackSessionContract.DESTINATION_MODE, action.mode.name)
                }
            }
            is JourneyAction.PlayCourse -> {
                val course = (journey.state.course as? CatalogState.Ready)?.data ?: return
                if (screen != VehicleScreen.COURSE_DETAIL || course.summary.id != action.courseId ||
                    course.stories.isEmpty() || (action.storyId != null && course.stories.none { it.id == action.storyId })) return
                sendCatalogPlayback(PlaybackSessionContract.START_COURSE, action.storyId) {
                    putString(PlaybackSessionContract.COURSE_ID, action.courseId)
                }
            }
            is JourneyAction.GuideFirstStop -> {
                val course = (journey.state.course as? CatalogState.Ready)?.data ?: return
                if (screen != VehicleScreen.COURSE_DETAIL || course.summary.id != action.courseId) return
                val first = course.stops.firstOrNull() ?: return
                guidanceConfirmation?.dismiss()
                val message = if (journey.state.navigation is VehicleNavigationState.Guiding) {
                    "현재 안내 목적지를 ${first.placeName}(으)로 변경합니다."
                } else first.placeName
                guidanceConfirmation = AlertDialog.Builder(this)
                    .setTitle("첫 장소로 안내할까요?")
                    .setMessage(message)
                    .setNegativeButton("취소", null)
                    .setPositiveButton("길 안내 시작") { _, _ ->
                        if (canUseParkedControls() && screen == VehicleScreen.COURSE_DETAIL &&
                            journey.selectedCourseId == action.courseId) {
                            journey.guideFirstStop(action.courseId) { confirmed ->
                                if (!isFinishing && !isDestroyed) {
                                    Toast.makeText(this, if (confirmed) "첫 장소 안내가 시작됐어요." else "길 안내를 시작하지 못했어요.",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }.show()
            }
        }
    }

    private fun sendCatalogPlayback(action: String, selectedId: String?, extras: Bundle.() -> Unit) {
        val bundle = Bundle().apply {
            putString(PlaybackSessionContract.SELECTED_STORY_ID, selectedId)
            extras()
        }
        val controls = controller?.transportControls
        if (controls == null) {
            pendingPlaybackAction = null
            pendingCatalogPlayback = CatalogPlaybackRequest(action, bundle)
        } else {
            controls.sendCustomAction(action, bundle)
        }
        screen = VehicleScreen.PLAYER
        showLocationPermission = false
        journey.show(screen)
        requestRender()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "toggle"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "next"
            KeyEvent.KEYCODE_MEDIA_STOP -> "stop"
            else -> null
        }
        if (action != null) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) playbackAction(action)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        renderHandler.removeCallbacksAndMessages(null)
        appMenu?.dismiss()
        guidanceConfirmation?.dismiss()
        libraryDialog?.dismiss()
        restrictions.close()
        userObservation?.close()
        libraryObservation?.close()
        controller?.unregisterCallback(mediaCallback)
        journey.close()
        pendingPlaybackAction = null
        pendingCatalogPlayback = null
        browser.disconnect()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        librarySync.syncNow()
        journey.show(VehicleUiPolicy.permittedScreen(screen, restriction))
        journey.start()
    }

    override fun onStop() {
        pendingPlaybackAction = null
        pendingCatalogPlayback = null
        requestingLocationPermission = false
        journey.stop()
        appMenu?.dismiss()
        guidanceConfirmation?.dismiss()
        libraryDialog?.dismiss()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen.name)
        outState.putString("courseId", journey.selectedCourseId)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val LOCATION_PERMISSION_REQUEST = 21
    }
}
