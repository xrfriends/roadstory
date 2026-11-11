package com.roadstory.pleos.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.media.MediaBrowserService
import com.roadstory.pleos.MainActivity
import com.roadstory.pleos.R
import com.roadstory.pleos.platform.CarUxRestrictionSource
import com.roadstory.pleos.platform.DrivingRestriction
import com.roadstory.pleos.platform.FailClosedDrivingRestrictionSource
import com.roadstory.pleos.platform.NavigationGateway
import com.roadstory.pleos.platform.NavigationSession
import com.roadstory.pleos.platform.VehicleLocationResult
import com.roadstory.pleos.platform.VehicleLocationSourceFactory
import com.roadstory.pleos.platform.VehicleNavigationState
import com.roadstory.pleos.user.LibraryItem
import com.roadstory.pleos.user.LibrarySyncMode
import com.roadstory.pleos.user.VehicleLibrarySync
import com.roadstory.pleos.user.VehicleUserStore

class RoadStoryPlaybackService : MediaBrowserService() {
    private lateinit var session: MediaSession
    private lateinit var focus: AudioFocusController
    private lateinit var api: StoryApiClient
    private lateinit var playbackLoader: StoryPlaybackLoader
    private lateinit var coordinator: PlaybackCoordinator
    private val mainHandler = Handler(Looper.getMainLooper())
    private val userStore by lazy { VehicleUserStore(this) }
    private val librarySync by lazy { VehicleLibrarySync.get(this) }
    private val checkpointStore by lazy { PlaybackCheckpointStore(this) }
    private var checkpointOwner: String? = null
    private var finishingPreviousOwnerStory = false
    private val location by lazy { VehicleLocationSourceFactory.create(this) }
    private val restrictions = FailClosedDrivingRestrictionSource { CarUxRestrictionSource(this) }
    private var restriction = DrivingRestriction.RESTRICTED
    private var userObservation: AutoCloseable? = null
    private var accountObservation: AutoCloseable? = null
    private var appliedNearbyPreferences: NearbyStoryPreferences? = null
    private var publishedAutoplay = false
    private var publishedStory: QueueStory? = null
    private var publishedAudioDuration: Long? = null
    private var locationRequest: AutoCloseable? = null
    private var routeNavigation: NavigationGateway? = null
    private var routeObservation: AutoCloseable? = null
    private var routeMode: DestinationMode? = null
    private var routeKey: RouteKey? = null
    private var routeEtaMinute: Int? = null
    private var routeGeneration = 0L

    private data class RouteKey(val revision: Long, val latitude: Double, val longitude: Double)

    private val saveAudioPosition = object : Runnable {
        override fun run() {
            coordinator.saveAudioPosition()
            mainHandler.postDelayed(this, 5_000L)
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) onSuspendEntering()
        }
    }

    override fun onCreate() {
        super.onCreate()
        api = StoryApiClient()
        playbackLoader = StoryPlaybackLoader(api, StoryAudioCache(this))
        focus = AudioFocusController(this, ::onAudioFocusChanged)
        checkpointOwner = librarySync.status.accountId
        coordinator = PlaybackCoordinator(
            loadNearby = ::loadNearby,
            loadStory = playbackLoader::load,
            player = createStorySpeechPlayer(this),
            requestFocus = focus::request,
            abandonFocus = focus::abandon,
            autoplayEnabled = { userStore.autoplay },
            initialStory = ::lastStory,
            dispatch = { event -> mainHandler.post { event() } },
            onState = ::publishState,
            onStoryLoaded = { story -> userStore.currentStory = story.toLibraryItem() },
            onStoryStarted = { story -> userStore.recordListening(story.toLibraryItem()) },
            clock = SystemClock::elapsedRealtime,
            onCheckpointChanged = { checkpoint ->
                if (!finishingPreviousOwnerStory) checkpointStore.save(checkpointOwner, checkpoint)
            },
            audioPlayer = AndroidStoryAudioPlayer(),
        )
        restrictions.observe { restriction = it }
        session = MediaSession(this, "RoadStory").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    val state = coordinator.snapshot
                    if (state.source == PlaybackSource.NEARBY && state.current == null && !canStartCatalog()) return
                    coordinator.play()
                }
                override fun onPause() = coordinator.pause()
                override fun onStop() = coordinator.stop()
                override fun onSkipToNext() = coordinator.next()

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (!validId(mediaId) || !canSelectStories()) return
                    restoreNewOwnerIfFinishing()
                    coordinator.playFrom(checkNotNull(mediaId))
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    when (action) {
                        PlaybackSessionContract.START_NEARBY -> if (canStartCatalog()) {
                            restoreNewOwnerIfFinishing()
                            coordinator.startNearby()
                        }
                        PlaybackSessionContract.FINISH_AFTER_CURRENT -> if (canSelectStories()) coordinator.finishAfterCurrent()
                        PlaybackSessionContract.DOWNLOAD_AUDIO -> if (canStartCatalog()) {
                            val id = extras?.getString(PlaybackSessionContract.SELECTED_STORY_ID)
                            if (validId(id)) downloadAudio(checkNotNull(id))
                        }
                        PlaybackSessionContract.START_DESTINATION -> if (canStartCatalog()) {
                            val mode = extras?.getString(PlaybackSessionContract.DESTINATION_MODE)
                                ?.let { name -> DestinationMode.entries.find { it.name == name } } ?: return
                            val selected = extras.getString(PlaybackSessionContract.SELECTED_STORY_ID)
                            if (selected == null || validId(selected)) {
                                restoreNewOwnerIfFinishing()
                                startDestination(mode, selected)
                            }
                        }
                        PlaybackSessionContract.START_COURSE -> if (canStartCatalog()) {
                            val courseId = extras?.getString(PlaybackSessionContract.COURSE_ID) ?: return
                            val selected = extras.getString(PlaybackSessionContract.SELECTED_STORY_ID)
                            if (validId(courseId) && (selected == null || validId(selected))) {
                                restoreNewOwnerIfFinishing()
                                coordinator.startPlan(PlaybackSource.COURSE, selected) { callback ->
                                    api.loadCourse(courseId) { callback(it.map(CourseDetail::stories)) }
                                }
                            }
                        }
                    }
                }
            })
            isActive = true
        }
        updateNearbyPreferences()
        checkpointStore.load(checkpointOwner)?.let(::restoreCheckpoint) ?: publishState(coordinator.snapshot)
        sessionToken = session.sessionToken
        accountObservation = librarySync.observe { status ->
            if (status.accountId == checkpointOwner) return@observe
            val expired = checkpointOwner != null && status.mode == LibrarySyncMode.SIGN_IN_REQUIRED
            if (!finishingPreviousOwnerStory) {
                coordinator.checkpointForRestart()?.let { checkpointStore.save(checkpointOwner, it) }
            }
            checkpointOwner = status.accountId
            finishingPreviousOwnerStory = expired
            if (expired) {
                releaseRouteObservation()
                coordinator.finishCurrentAfterAccountExpiry()
                if (coordinator.snapshot.phase == PlaybackPhase.STOPPED) restoreNewOwnerIfFinishing()
            } else {
                restoreCheckpoint(checkpointStore.load(checkpointOwner))
            }
            updateNearbyPreferences()
        }
        userObservation = userStore.observe {
            updateNearbyPreferences()
            if (publishedAutoplay != userStore.autoplay) publishState(coordinator.snapshot)
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun restoreCheckpoint(checkpoint: PlaybackCheckpoint?) {
        val before = coordinator.snapshot
        coordinator.restoreCheckpoint(checkpoint)
        checkpoint?.story?.let { userStore.currentStory = LibraryItem(it.id, it.title, it.placeName, it.category) }
        if (coordinator.snapshot == before) publishState(before)
    }

    private fun restoreNewOwnerIfFinishing() {
        if (!finishingPreviousOwnerStory) return
        finishingPreviousOwnerStory = false
        restoreCheckpoint(checkpointStore.load(checkpointOwner))
    }

    private fun canSelectStories() =
        !restriction.blocksDetailedContent && !restrictions.current().blocksDetailedContent

    private fun canStartCatalog() = canSelectStories() && session.currentControllerInfo.uid == applicationInfo.uid

    private fun validId(id: String?) = id != null && id.length <= 128 && id.matches(Regex("[A-Za-z0-9_-]+"))

    private fun downloadAudio(id: String) {
        val requestedBy = checkpointOwner
        playbackLoader.download(id) { result ->
            mainHandler.post {
                if (checkpointOwner == requestedBy) {
                    session.sendSessionEvent(PlaybackSessionContract.DOWNLOAD_AUDIO_RESULT, Bundle().apply {
                        putString(PlaybackSessionContract.SELECTED_STORY_ID, id)
                        putBoolean(PlaybackSessionContract.DOWNLOAD_SUCCEEDED, result.isSuccess)
                    })
                }
            }
        }
    }

    private fun loadNearby(callback: (kotlin.Result<List<QueueStory>>) -> Unit) {
        if (!canSelectStories()) {
            callback(kotlin.Result.failure(CandidateUnavailable(PlaybackIssue.LOCATION_UNAVAILABLE)))
            return
        }
        locationRequest?.close()
        locationRequest = location.request { result ->
            locationRequest = null
            when (result) {
                is VehicleLocationResult.Available -> if (canSelectStories()) {
                    api.loadNearby(result.fix, callback)
                } else {
                    callback(kotlin.Result.failure(CandidateUnavailable(PlaybackIssue.LOCATION_UNAVAILABLE)))
                }
                is VehicleLocationResult.PermissionRequired ->
                    callback(kotlin.Result.failure(CandidateUnavailable(PlaybackIssue.LOCATION_PERMISSION)))
                VehicleLocationResult.Unavailable ->
                    callback(kotlin.Result.failure(CandidateUnavailable(PlaybackIssue.LOCATION_UNAVAILABLE)))
            }
        }
    }

    private fun startDestination(mode: DestinationMode, selected: String?) {
        releaseRouteObservation()
        val navigation = NavigationSession.acquire(this)
        routeNavigation = navigation
        routeMode = mode
        val currentGuidance = navigation.current() as? VehicleNavigationState.Guiding
        routeKey = currentGuidance?.key()
        routeEtaMinute = currentGuidance?.destination?.remainingDurationSeconds?.div(60)
        coordinator.startPlan(PlaybackSource.DESTINATION, selected) { callback ->
            val guiding = navigation.current() as? VehicleNavigationState.Guiding
            if (guiding == null || (guiding.destination.remainingDurationSeconds ?: 0) < 60) {
                callback(kotlin.Result.failure(CandidateUnavailable(PlaybackIssue.DESTINATION_UNAVAILABLE)))
            } else {
                val key = guiding.key()
                api.loadDestination(guiding.destination, mode) { result ->
                    mainHandler.post {
                        if ((navigation.current() as? VehicleNavigationState.Guiding)?.key() == key) {
                            callback(result.map(DestinationPlan::stories))
                        } else {
                            callback(kotlin.Result.failure(CandidateUnavailable(PlaybackIssue.DESTINATION_UNAVAILABLE)))
                        }
                    }
                }
            }
        }
        routeObservation = navigation.observe(::onRouteChanged)
    }

    private fun VehicleNavigationState.Guiding.key() =
        RouteKey(routeRevision, destination.latitude, destination.longitude)

    private fun onRouteChanged(state: VehicleNavigationState) {
        val mode = routeMode ?: return
        val guiding = (state as? VehicleNavigationState.Guiding)
            ?.takeIf { (it.destination.remainingDurationSeconds ?: 0) >= 60 }
        val key = guiding?.key()
        val minutes = guiding?.destination?.remainingDurationSeconds?.div(60)
        if (key == routeKey && minutes == routeEtaMinute) return
        routeKey = key
        routeEtaMinute = minutes
        val generation = ++routeGeneration
        // Drop obsolete next items immediately; never interrupt the current narration.
        coordinator.replaceDestinationTail(emptyList())
        val current = coordinator.snapshot.current
        val destination = guiding?.destination ?: return
        if (current == null) return
        // TTS does not expose progress. Reserve the current story's whole estimated duration.
        val available = (destination.remainingDurationSeconds ?: 0) - current.durationSeconds
        if (available < 60) return
        api.loadDestination(destination.copy(remainingDurationSeconds = available), mode) { result ->
            mainHandler.post {
                if (generation == routeGeneration) {
                    result.onSuccess { coordinator.replaceDestinationTail(it.stories) }
                }
            }
        }
    }

    private fun releaseRouteObservation() {
        routeGeneration++
        routeMode = null
        routeKey = null
        routeEtaMinute = null
        routeObservation?.close()
        routeObservation = null
        routeNavigation?.close()
        routeNavigation = null
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = BrowserRoot("road-story", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) =
        result.sendResult(mutableListOf())

    fun onSuspendEntering() = coordinator.suspendPlayback()

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                coordinator.transientFocusLoss()
            AudioManager.AUDIOFOCUS_GAIN -> coordinator.focusGained()
            AudioManager.AUDIOFOCUS_LOSS -> coordinator.suspendPlayback()
        }
    }

    private fun updateNearbyPreferences() {
        val preferences = NearbyStoryPreferences(
            frequencies = VehicleUserStore.topics.associate { it.key to userStore.frequency(it) },
            familyMode = userStore.familyMode,
            dislikedStoryIds = userStore.dislikedStoryIds,
            dislikedCategories = userStore.dislikedCategories,
        )
        if (preferences == appliedNearbyPreferences) return
        appliedNearbyPreferences = preferences
        coordinator.updateNearbyPreferences(preferences)
    }

    private fun lastStory() = userStore.currentStory.let { QueueStory(it.id, it.title, it.subtitle, category = it.category) }
    private fun StorySpeech.toLibraryItem() = LibraryItem(id, title, placeName, category)

    private fun publishState(snapshot: PlaybackSnapshot) {
        if (finishingPreviousOwnerStory && snapshot.phase == PlaybackPhase.STOPPED) {
            restoreNewOwnerIfFinishing()
            return
        }
        if (snapshot.source != PlaybackSource.DESTINATION && routeNavigation != null) releaseRouteObservation()
        if (snapshot.phase != PlaybackPhase.BUFFERING) {
            locationRequest?.close()
            locationRequest = null
        }
        val story = snapshot.current?.takeIf { it.title.isNotBlank() } ?: lastStory()
        if (story != publishedStory || publishedAudioDuration != snapshot.audioDurationMs) {
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, story.id)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, story.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Road Story")
                    .apply { snapshot.audioDurationMs?.let { putLong(MediaMetadata.METADATA_KEY_DURATION, it) } }
                    .build(),
            )
            publishedStory = story
            publishedAudioDuration = snapshot.audioDurationMs
        }
        val state = when (snapshot.phase) {
            PlaybackPhase.IDLE -> PlaybackState.STATE_NONE
            PlaybackPhase.BUFFERING -> PlaybackState.STATE_BUFFERING
            PlaybackPhase.PLAYING -> PlaybackState.STATE_PLAYING
            PlaybackPhase.PAUSED -> PlaybackState.STATE_PAUSED
            PlaybackPhase.STOPPED, PlaybackPhase.EMPTY -> PlaybackState.STATE_STOPPED
            PlaybackPhase.ERROR -> PlaybackState.STATE_ERROR
        }
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        if (snapshot.next != null) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        publishedAutoplay = userStore.autoplay
        val extras = Bundle().apply {
            putString(PlaybackSessionContract.CURRENT_ID, snapshot.current?.id)
            putString(PlaybackSessionContract.CURRENT_TITLE, snapshot.current?.title)
            putString(PlaybackSessionContract.CURRENT_PLACE, snapshot.current?.placeName)
            putString(PlaybackSessionContract.CURRENT_CATEGORY, snapshot.current?.category)
            putString(PlaybackSessionContract.NEXT_TITLE, snapshot.next?.title)
            putInt(PlaybackSessionContract.NEXT_DURATION_SECONDS, snapshot.next?.durationSeconds ?: 0)
            putInt(PlaybackSessionContract.CURRENT_DURATION_SECONDS, story.durationSeconds)
            putBoolean(PlaybackSessionContract.END_AFTER_CURRENT, snapshot.endAfterCurrent)
            putBoolean(PlaybackSessionContract.AUTOPLAY_ENABLED, publishedAutoplay)
            putBoolean(PlaybackSessionContract.QUEUE_EMPTY, snapshot.phase == PlaybackPhase.EMPTY)
            putBoolean(PlaybackSessionContract.FILTERED_BY_PREFERENCES, snapshot.filteredByPreferences)
            putString(PlaybackSessionContract.SOURCE, snapshot.source?.name)
            putString(PlaybackSessionContract.ISSUE, snapshot.issue?.name)
            putLong(PlaybackSessionContract.LISTENED_MS, snapshot.listenedMs)
            putLong(PlaybackSessionContract.LISTENING_SINCE_ELAPSED_MS, snapshot.listeningSinceElapsedMs ?: -1L)
            putBoolean(PlaybackSessionContract.RESUME_AT_SENTENCE, snapshot.resumeAtSentence)
            putInt(PlaybackSessionContract.CURRENT_SENTENCE_INDEX, snapshot.sentenceIndex)
            putLong(PlaybackSessionContract.AUDIO_POSITION_MS, snapshot.audioPositionMs ?: -1L)
            putLong(PlaybackSessionContract.AUDIO_DURATION_MS, snapshot.audioDurationMs ?: -1L)
            putLong(PlaybackSessionContract.AUDIO_POSITION_UPDATED_AT_MS, snapshot.audioPositionUpdatedAtMs ?: -1L)
            putBoolean(PlaybackSessionContract.OFFLINE_AUDIO, snapshot.offlineAudio)
        }
        val builder = PlaybackState.Builder().setActions(actions).setExtras(extras).setState(
            state,
            snapshot.audioPositionMs ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN,
            if (snapshot.phase == PlaybackPhase.PLAYING) 1f else 0f,
            snapshot.audioPositionUpdatedAtMs ?: SystemClock.elapsedRealtime(),
        )
        snapshot.error?.let(builder::setErrorMessage)
        if (snapshot.phase == PlaybackPhase.PLAYING) {
            try {
                startForeground(101, playbackNotification(story.title))
            } catch (_: ForegroundServiceStartNotAllowedException) {
                coordinator.suspendPlayback()
                return
            }
        }
        session.setPlaybackState(builder.build())
        mainHandler.removeCallbacks(saveAudioPosition)
        if (snapshot.phase == PlaybackPhase.PLAYING && snapshot.audioPositionMs != null && !finishingPreviousOwnerStory) {
            mainHandler.postDelayed(saveAudioPosition, 5_000L)
        }
        // Keep foreground status between sentences and during transient audio focus loss.
        if (snapshot.phase != PlaybackPhase.PLAYING && snapshot.phase != PlaybackPhase.BUFFERING && !snapshot.awaitingFocusGain) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun playbackNotification(title: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("playback", getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW))
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("Road Story")
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        userObservation?.close()
        accountObservation?.close()
        locationRequest?.close()
        releaseRouteObservation()
        coordinator.close()
        mainHandler.removeCallbacksAndMessages(null)
        api.close()
        restrictions.close()
        focus.close()
        session.release()
        super.onDestroy()
    }
}
