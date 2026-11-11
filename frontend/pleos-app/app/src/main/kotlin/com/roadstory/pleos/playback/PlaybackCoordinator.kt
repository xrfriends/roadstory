package com.roadstory.pleos.playback

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.Locale

enum class PlaybackPhase { IDLE, BUFFERING, PLAYING, PAUSED, STOPPED, EMPTY, ERROR }
enum class PlaybackSource { NEARBY, SELECTED, DESTINATION, COURSE }
enum class PlaybackIssue { LOCATION_PERMISSION, LOCATION_UNAVAILABLE, DESTINATION_UNAVAILABLE, OFFLINE_AUDIO_UNAVAILABLE }

internal class CandidateUnavailable(val issue: PlaybackIssue) : RuntimeException()
typealias PlaybackCandidateLoader = ((Result<List<QueueStory>>) -> Unit) -> Unit

data class PlaybackCheckpoint(
    val story: QueueStory,
    val narrationFingerprint: String,
    val sentenceIndex: Int,
    val listenedMs: Long,
    val audioPositionMs: Long? = null,
    val audioDurationMs: Long? = null,
)

data class PlaybackSnapshot(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val current: QueueStory? = null,
    val next: QueueStory? = null,
    val endAfterCurrent: Boolean = false,
    val error: String? = null,
    val awaitingFocusGain: Boolean = false,
    val filteredByPreferences: Boolean = false,
    val source: PlaybackSource? = null,
    val issue: PlaybackIssue? = null,
    val listenedMs: Long = 0L,
    val listeningSinceElapsedMs: Long? = null,
    val resumeAtSentence: Boolean = false,
    val sentenceIndex: Int = -1,
    val audioPositionMs: Long? = null,
    val audioDurationMs: Long? = null,
    val audioPositionUpdatedAtMs: Long? = null,
    val offlineAudio: Boolean = false,
)

/** Owns one listening session. Commands and dispatched callbacks must run on the same thread. */
class PlaybackCoordinator(
    private val loadNearby: PlaybackCandidateLoader,
    private val loadStory: (String, (Result<StorySpeech>) -> Unit) -> Unit,
    private val player: StorySpeechPlayer,
    private val requestFocus: () -> Boolean,
    private val abandonFocus: () -> Unit,
    private val autoplayEnabled: () -> Boolean,
    private val initialStory: () -> QueueStory,
    private val dispatch: (() -> Unit) -> Unit,
    private val onState: (PlaybackSnapshot) -> Unit,
    private val onStoryLoaded: (StorySpeech) -> Unit,
    private val onStoryStarted: (StorySpeech) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onCheckpointChanged: (PlaybackCheckpoint?) -> Unit = {},
    private val audioPlayer: StoryAudioPlayer? = null,
) : AutoCloseable {
    private sealed interface StartRequest {
        data object Nearby : StartRequest
        data class Selected(val story: QueueStory) : StartRequest
        data class Plan(
            val source: PlaybackSource,
            val selectedId: String?,
            val load: PlaybackCandidateLoader,
        ) : StartRequest
    }

    private data class FilePlayback(
        val source: CachedStoryAudio,
        val offline: Boolean,
        var positionMs: Long,
        var durationMs: Long,
        var finished: Boolean = false,
    )

    private var startRequest: StartRequest? = null
    private var candidatePool = emptyList<QueueStory>()
    private val selectedIds = mutableSetOf<String>()
    private var current: QueueStory? = null
    private var next: QueueStory? = null
    private var nearbyPreferences = NearbyStoryPreferences()
    private var loaded: StorySpeech? = null
    private var filePlayback: FilePlayback? = null
    private var filePlaying = false
    private var generation = 0L
    private var speaking = false
    private var resumeOnFocusGain = false
    private var endAfterCurrent = false
    private var finishingExpiredAccountStory = false
    private var sentenceChunks = emptyList<String>()
    private var narrationFingerprint: String? = null
    private var sentenceIndex = 0
    private var storyStarted = false
    private var listenedMs = 0L
    private var listeningSinceElapsedMs: Long? = null
    private var pendingCheckpoint: PlaybackCheckpoint? = null
    private var lastReportedCheckpoint: PlaybackCheckpoint? = null
    private var utteranceGeneration = 0L
    private var closed = false

    var snapshot = PlaybackSnapshot()
        private set

    fun checkpointForRestart(): PlaybackCheckpoint? {
        if (finishingExpiredAccountStory) return null
        pendingCheckpoint?.let { return it }
        val story = current ?: return null
        filePlayback?.let { file ->
            if (!storyStarted || file.finished) return null
            val position = currentAudioPosition(file)
            if (position >= file.durationMs) return null
            return PlaybackCheckpoint(story, file.source.asset.sha256, 0, currentListenedMs(), position, file.durationMs)
        }
        val fingerprint = narrationFingerprint ?: return null
        if (!storyStarted || sentenceIndex !in sentenceChunks.indices) return null
        return PlaybackCheckpoint(story, fingerprint, sentenceIndex, currentListenedMs())
    }

    fun saveAudioPosition() {
        if (filePlaying && snapshot.phase == PlaybackPhase.PLAYING) {
            filePlayback?.let { it.positionMs = currentAudioPosition(it) }
            reportCheckpoint()
        }
    }

    /** Reset the session for the current owner and wait for an explicit Play command. */
    fun restoreCheckpoint(checkpoint: PlaybackCheckpoint?) {
        if (closed) return
        resetPlayback()
        lastReportedCheckpoint = checkpoint
        if (checkpoint == null) {
            publish(PlaybackPhase.STOPPED)
            return
        }
        startRequest = StartRequest.Selected(checkpoint.story)
        select(checkpoint.story)
        pendingCheckpoint = checkpoint
        listenedMs = checkpoint.listenedMs.coerceAtLeast(0L)
        publish(PlaybackPhase.PAUSED)
    }

    fun play() {
        if (closed || isActive()) return
        resumePlayback(focusGranted = false)
    }

    fun startNearby() {
        if (closed) return
        endAfterCurrent = false
        startSession(StartRequest.Nearby)
    }

    fun playFrom(id: String) {
        if (closed || id.isBlank()) return
        endAfterCurrent = false
        val selected = initialStory().takeIf { it.id == id } ?: QueueStory(id, "")
        startSession(StartRequest.Selected(selected))
    }

    fun startPlan(source: PlaybackSource, selectedId: String? = null, load: PlaybackCandidateLoader) {
        require(source == PlaybackSource.COURSE || source == PlaybackSource.DESTINATION)
        if (closed) return
        endAfterCurrent = false
        startSession(StartRequest.Plan(source, selectedId, load))
    }

    /** Replace only the unplayed part of an existing destination session. */
    fun replaceDestinationTail(stories: List<QueueStory>) {
        if (closed || current == null || (startRequest as? StartRequest.Plan)?.source != PlaybackSource.DESTINATION) return
        candidatePool = stories.validCandidates()
        updateNext()
        publish(snapshot.phase, snapshot.error, snapshot.issue)
    }

    fun next() {
        if (closed) return
        advance(focusGranted = false)
    }

    fun updateNearbyPreferences(preferences: NearbyStoryPreferences) {
        if (closed || preferences == nearbyPreferences) return
        nearbyPreferences = preferences
        if (current != null) {
            updateNext()
            publish(snapshot.phase, snapshot.error, snapshot.issue)
        }
    }

    fun pause() {
        if (closed || (!isActive() && !resumeOnFocusGain)) return
        if (finishingExpiredAccountStory) {
            stop()
            return
        }
        interrupt()
        resumeOnFocusGain = false
        ignoreCleanupFailure(abandonFocus)
        reportCheckpoint()
        publish(PlaybackPhase.PAUSED)
    }

    /** Retain a started story for explicit replay after the display or vehicle suspends. */
    fun suspendPlayback() {
        if (closed) return
        if ((!storyStarted && pendingCheckpoint == null) || current == null || finishingExpiredAccountStory) {
            stop()
            return
        }
        interrupt(release = true)
        resumeOnFocusGain = false
        ignoreCleanupFailure(abandonFocus)
        reportCheckpoint()
        publish(PlaybackPhase.PAUSED)
    }

    fun stop() {
        if (closed) return
        resetPlayback()
        reportCheckpoint(force = true)
        publish(PlaybackPhase.STOPPED)
    }

    fun finishAfterCurrent() {
        if (closed || startRequest == null || endAfterCurrent) return
        endAfterCurrent = true
        publish(snapshot.phase, snapshot.error, snapshot.issue)
    }

    /** Let an active public story that started speaking finish after authentication expires. */
    fun finishCurrentAfterAccountExpiry() {
        if (closed) return
        if (!storyStarted || !isActive()) {
            stop()
            return
        }
        startRequest = null
        candidatePool = emptyList()
        selectedIds.clear()
        next = null
        loaded = null
        resumeOnFocusGain = false
        endAfterCurrent = true
        finishingExpiredAccountStory = true
        publish(snapshot.phase)
    }

    fun transientFocusLoss() {
        if (closed || !isActive()) return
        if (finishingExpiredAccountStory) {
            stop()
            return
        }
        interrupt()
        resumeOnFocusGain = true
        reportCheckpoint()
        publish(PlaybackPhase.PAUSED)
    }

    fun focusGained() {
        if (closed || !resumeOnFocusGain) return
        resumePlayback(focusGranted = true)
    }

    override fun close() {
        if (closed) return
        closed = true
        interrupt(release = true)
        reportCheckpoint()
        ignoreCleanupFailure(abandonFocus)
    }

    private fun resetPlayback() {
        interrupt(release = true)
        startRequest = null
        clearQueue()
        resumeOnFocusGain = false
        endAfterCurrent = false
        finishingExpiredAccountStory = false
        ignoreCleanupFailure(abandonFocus)
    }

    private fun resumePlayback(focusGranted: Boolean) {
        if (current != null) {
            if (filePlayback?.finished == true || (sentenceChunks.isNotEmpty() && sentenceIndex == sentenceChunks.size)) {
                resetNarration()
            }
            beginPlayback(focusGranted)?.let(::loadCurrent)
        } else {
            val request = startRequest ?: StartRequest.Selected(initialStory())
            startSession(request, focusGranted)
        }
    }

    private fun startSession(request: StartRequest, focusGranted: Boolean = false) {
        finishingExpiredAccountStory = false
        startRequest = request
        clearQueue()
        reportCheckpoint()
        val token = beginPlayback(focusGranted) ?: return
        if (request is StartRequest.Selected) {
            // Direct replay never needs location and doesn't silently attach a nearby queue.
            select(request.story)
            publish(PlaybackPhase.BUFFERING)
            loadCurrent(token)
            return
        }
        val load = (request as? StartRequest.Plan)?.load ?: loadNearby
        load { result -> dispatchIfCurrent(token) {
            val candidates = result.getOrElse { error ->
                val issue = (error as? CandidateUnavailable)?.issue ?:
                    PlaybackIssue.OFFLINE_AUDIO_UNAVAILABLE.takeIf { error.isConnectionFailure() }
                fail(
                    when (issue) {
                        PlaybackIssue.LOCATION_PERMISSION -> "주변 이야기를 들으려면 위치 권한이 필요합니다."
                        PlaybackIssue.LOCATION_UNAVAILABLE -> "현재 위치를 확인하지 못했습니다."
                        PlaybackIssue.DESTINATION_UNAVAILABLE -> "내비게이션 안내 정보를 확인하지 못했습니다."
                        PlaybackIssue.OFFLINE_AUDIO_UNAVAILABLE -> "차량에 저장한 이야기를 선택해 주세요."
                        null -> if (request is StartRequest.Plan) "이야기 편성을 불러오지 못했습니다." else "주변 이야기를 불러오지 못했습니다."
                    },
                    issue,
                )
                return@dispatchIfCurrent
            }.validCandidates()
            candidatePool = candidates
            val first = when (request) {
                StartRequest.Nearby -> nearbyPreferences.firstEligible(candidatePool, selectedIds)
                is StartRequest.Selected -> error("Direct selection does not load candidates")
                is StartRequest.Plan -> {
                    val selectedIndex = candidates.indexOfFirst { it.id == request.selectedId }
                    if (selectedIndex >= 0) {
                        candidatePool = candidates.drop(selectedIndex + 1)
                        candidates[selectedIndex]
                    } else if (request.selectedId == null) {
                        firstRemaining()
                    } else {
                        // A route/course may have changed since the selected row was shown.
                        fail("선택한 이야기가 편성에서 변경되었습니다. 목록을 다시 확인해 주세요.")
                        return@dispatchIfCurrent
                    }
                }
            }
            if (first == null) {
                settle(PlaybackPhase.EMPTY)
            } else {
                select(first)
                publish(PlaybackPhase.BUFFERING)
                loadCurrent(token)
            }
        } }
    }

    private fun advance(focusGranted: Boolean) {
        val upcoming = next ?: return
        select(upcoming)
        reportCheckpoint()
        beginPlayback(focusGranted)?.let(::loadCurrent)
    }

    private fun clearQueue() {
        candidatePool = emptyList()
        selectedIds.clear()
        current = null
        next = null
        loaded = null
        resetNarration()
    }

    private fun select(story: QueueStory) {
        current = story
        selectedIds += story.id
        loaded = null
        resetNarration()
        updateNext()
    }

    private fun resetNarration() {
        filePlayback = null
        sentenceChunks = emptyList()
        narrationFingerprint = null
        sentenceIndex = 0
        storyStarted = false
        listenedMs = 0L
        listeningSinceElapsedMs = null
        pendingCheckpoint = null
    }

    private fun updateNext() {
        next = firstRemaining()
    }

    private fun firstRemaining() = if (startRequest is StartRequest.Plan) {
        candidatePool.firstOrNull { nearbyPreferences.isEligible(it, selectedIds) }
    } else {
        nearbyPreferences.firstEligible(candidatePool, selectedIds)
    }

    private fun List<QueueStory>.validCandidates() = filter { it.id.isNotBlank() }.distinctBy { it.id }

    private fun beginPlayback(focusGranted: Boolean): Long? {
        val token = interrupt()
        resumeOnFocusGain = false
        val acquired = focusGranted || try {
            requestFocus()
        } catch (_: RuntimeException) {
            false
        }
        if (!acquired) {
            fail("오디오 재생을 시작할 수 없습니다.")
            return null
        }
        publish(PlaybackPhase.BUFFERING)
        return token
    }

    private fun loadCurrent(token: Long) {
        if (generation != token) return
        val selected = checkNotNull(current)
        val cached = loaded
        if (cached?.id == selected.id && cached.localAudio?.isAvailable() != false) {
            if (sentenceChunks.isEmpty() && filePlayback == null) {
                preparePlayback(cached)
                publish(PlaybackPhase.BUFFERING)
            }
            playLoaded(cached, token)
            return
        }
        loadStory(selected.id) { result -> dispatchIfCurrent(token) {
            val story = result.getOrNull()
            if (story == null) {
                if (result.exceptionOrNull() is OfflineAudioUnavailable) {
                    fail("이 이야기에 저장된 음원이 없어요.", PlaybackIssue.OFFLINE_AUDIO_UNAVAILABLE)
                } else {
                    fail("이야기를 불러오지 못했습니다.")
                }
                return@dispatchIfCurrent
            }
            if (story.id != selected.id) {
                resetNarration()
                fail("이야기를 불러오지 못했습니다.")
                return@dispatchIfCurrent
            }
            val resolved = story.copy(category = story.category ?: selected.category)
            val checkpoint = checkpointForRestart()
            loaded = resolved
            current = resolved.toQueueStory()
            resetNarration()
            pendingCheckpoint = checkpoint
            listenedMs = checkpoint?.listenedMs ?: 0L
            preparePlayback(resolved)
            onStoryLoaded(resolved)
            if (generation != token) return@dispatchIfCurrent
            publish(PlaybackPhase.BUFFERING)
            playLoaded(resolved, token)
        } }
    }

    private fun preparePlayback(story: StorySpeech) {
        val audio = story.localAudio
        val checkpoint = pendingCheckpoint
        // Preserve a matching sentence checkpoint created before an audio file was attached.
        val hasSentenceCheckpoint = checkpoint != null && checkpoint.audioPositionMs == null && story.text.isNotBlank()
        if (audio == null || audioPlayer == null || hasSentenceCheckpoint) {
            prepareNarration(story.text)
            return
        }
        pendingCheckpoint = null
        val position = checkpoint?.audioPositionMs
        val duration = checkpoint?.audioDurationMs
        val matches = checkpoint?.narrationFingerprint == audio.asset.sha256 &&
            position != null && duration != null && position in 0 until duration
        filePlayback = FilePlayback(audio, story.offlineAudio, if (matches) checkNotNull(position) else 0L,
            if (matches) checkNotNull(duration) else audio.asset.durationMs)
        if (matches) {
            storyStarted = true
        } else {
            listenedMs = 0L
            reportCheckpoint()
        }
    }

    private fun playLoaded(story: StorySpeech, token: Long) {
        val file = filePlayback
        if (file == null) speak(story, token) else playFile(story, file, token)
    }

    private fun playFile(story: StorySpeech, file: FilePlayback, token: Long) {
        if (generation != token) return
        val adapter = checkNotNull(audioPlayer)
        filePlaying = true
        val onError = { dispatchIfCurrent(token) {
            if (finishingExpiredAccountStory) stop() else fail("저장된 음원을 재생하지 못했습니다.")
        } }
        try {
            adapter.play(file.source.file, file.positionMs,
                onStart = { position, duration -> dispatchIfCurrent(token) {
                    if (snapshot.phase != PlaybackPhase.BUFFERING) return@dispatchIfCurrent
                    file.durationMs = duration
                    file.positionMs = position.coerceIn(0L, duration)
                    startAudible(story, token)
                } },
                onDone = { dispatchIfCurrent(token) {
                    filePlaying = false
                    file.finished = true
                    file.positionMs = file.durationMs
                    captureListeningTime()
                    reportCheckpoint()
                    completeStory()
                } },
                onError = onError,
            )
        } catch (_: RuntimeException) {
            onError()
        }
    }

    private fun speak(story: StorySpeech, token: Long) {
        if (generation != token) return
        val utterance = ++utteranceGeneration
        var utteranceStarted = false
        speaking = true
        val onError = { _: String -> dispatchUtterance(token, utterance, ::failNarration) }
        try {
            player.speak(
                sentenceChunks[sentenceIndex],
                onStart = { dispatchUtterance(token, utterance) {
                    if (!utteranceStarted && snapshot.phase == PlaybackPhase.BUFFERING) {
                        utteranceStarted = true
                        startAudible(story, token)
                    }
                } },
                onDone = { dispatchUtterance(token, utterance) {
                    if (!utteranceStarted || snapshot.phase != PlaybackPhase.PLAYING) {
                        failNarration()
                        return@dispatchUtterance
                    }
                    captureListeningTime()
                    speaking = false
                    sentenceIndex++
                    reportCheckpoint()
                    if (sentenceIndex < sentenceChunks.size) {
                        publish(PlaybackPhase.BUFFERING)
                        speak(story, token)
                    } else {
                        completeStory()
                    }
                } },
                onError = onError,
            )
        } catch (_: RuntimeException) {
            onError("")
        }
    }

    private fun startAudible(story: StorySpeech, token: Long) {
        listeningSinceElapsedMs = clock()
        if (!storyStarted) {
            storyStarted = true
            onStoryStarted(story)
        }
        if (generation != token) return
        reportCheckpoint()
        publish(PlaybackPhase.PLAYING)
    }

    private fun completeStory() {
        when {
            finishingExpiredAccountStory -> stop()
            autoplayEnabled() && !endAfterCurrent && next != null -> advance(focusGranted = true)
            else -> settle(PlaybackPhase.PAUSED)
        }
    }

    private fun failNarration() {
        if (finishingExpiredAccountStory) stop() else fail("음성을 합성하지 못했습니다.")
    }

    private fun prepareNarration(text: String) {
        sentenceChunks = splitSentences(text)
        narrationFingerprint = fingerprint(sentenceChunks)
        val checkpoint = pendingCheckpoint ?: return
        pendingCheckpoint = null
        if (checkpoint.audioPositionMs == null && checkpoint.narrationFingerprint == narrationFingerprint &&
            checkpoint.sentenceIndex in sentenceChunks.indices && checkpoint.listenedMs >= 0L) {
            sentenceIndex = checkpoint.sentenceIndex
            storyStarted = true
        } else {
            listenedMs = 0L
            reportCheckpoint()
        }
    }

    private fun fingerprint(sentences: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sentenceLength = ByteBuffer.allocate(Int.SIZE_BYTES)
        for (sentence in sentences) {
            val bytes = sentence.toByteArray(Charsets.UTF_8)
            sentenceLength.putInt(0, bytes.size)
            digest.update(sentenceLength.array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun splitSentences(text: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.KOREA).apply { setText(text) }
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf(String::isNotEmpty)?.let(result::add)
            start = end
            end = iterator.next()
        }
        return result.ifEmpty { listOf(text) }
    }

    private fun fail(error: String, issue: PlaybackIssue? = null) = settle(PlaybackPhase.ERROR, error, issue)

    private fun settle(phase: PlaybackPhase, error: String? = null, issue: PlaybackIssue? = null) {
        interrupt()
        ignoreCleanupFailure(abandonFocus)
        reportCheckpoint()
        publish(phase, error, issue)
    }

    private fun interrupt(release: Boolean = false): Long {
        val token = ++generation
        utteranceGeneration++
        filePlayback?.let { it.positionMs = currentAudioPosition(it) }
        if (filePlaying || release) audioPlayer?.let { ignoreCleanupFailure(it::stop) }
        filePlaying = false
        captureListeningTime()
        val wasSpeaking = speaking
        speaking = false
        if (wasSpeaking) ignoreCleanupFailure(player::stop)
        if (wasSpeaking || release) ignoreCleanupFailure(player::release)
        return token
    }

    private fun currentAudioPosition(file: FilePlayback): Long {
        val live = if (filePlaying && listeningSinceElapsedMs != null) audioPlayer?.positionMs else null
        return (live ?: file.positionMs).coerceIn(0L, file.durationMs)
    }

    private fun captureListeningTime() {
        listenedMs = currentListenedMs()
        listeningSinceElapsedMs = null
    }

    private fun currentListenedMs(): Long {
        val since = listeningSinceElapsedMs ?: return listenedMs
        return listenedMs + (clock() - since).coerceIn(0L, Long.MAX_VALUE - listenedMs)
    }

    private fun reportCheckpoint(force: Boolean = false) {
        val checkpoint = checkpointForRestart()
        if (!force && lastReportedCheckpoint == checkpoint) return
        lastReportedCheckpoint = checkpoint
        onCheckpointChanged(checkpoint)
    }

    private fun ignoreCleanupFailure(cleanup: () -> Unit) {
        try {
            cleanup()
        } catch (_: RuntimeException) {
            // The request was invalidated before cleanup, even if the adapter disconnected.
        }
    }

    private fun dispatchIfCurrent(token: Long, action: () -> Unit) {
        dispatch { if (!closed && generation == token) action() }
    }

    private fun dispatchUtterance(token: Long, utterance: Long, action: () -> Unit) {
        dispatchIfCurrent(token) { if (utteranceGeneration == utterance) action() }
    }

    private fun isActive() = snapshot.phase == PlaybackPhase.BUFFERING || snapshot.phase == PlaybackPhase.PLAYING

    private fun publish(phase: PlaybackPhase, error: String? = null, issue: PlaybackIssue? = null) {
        val file = filePlayback
        val audioPosition = file?.let(::currentAudioPosition) ?: pendingCheckpoint?.audioPositionMs
        val updated = PlaybackSnapshot(
            phase = phase,
            current = current,
            next = next,
            endAfterCurrent = endAfterCurrent,
            error = error,
            awaitingFocusGain = resumeOnFocusGain,
            filteredByPreferences = phase == PlaybackPhase.EMPTY && candidatePool.isNotEmpty(),
            source = when (val request = startRequest) {
                StartRequest.Nearby -> PlaybackSource.NEARBY
                is StartRequest.Selected -> PlaybackSource.SELECTED
                is StartRequest.Plan -> request.source
                null -> null
            },
            issue = issue,
            listenedMs = listenedMs,
            listeningSinceElapsedMs = listeningSinceElapsedMs,
            resumeAtSentence = audioPosition == null && phase == PlaybackPhase.PAUSED &&
                (pendingCheckpoint != null || (storyStarted && sentenceIndex < sentenceChunks.size)),
            sentenceIndex = if (audioPosition != null) -1 else
                pendingCheckpoint?.sentenceIndex ?: sentenceIndex.takeIf { it in sentenceChunks.indices } ?: -1,
            audioPositionMs = audioPosition,
            audioDurationMs = file?.durationMs ?: pendingCheckpoint?.audioDurationMs,
            audioPositionUpdatedAtMs = clock().takeIf { file != null && phase == PlaybackPhase.PLAYING },
            offlineAudio = file?.offline == true || issue == PlaybackIssue.OFFLINE_AUDIO_UNAVAILABLE,
        )
        if (updated != snapshot) {
            snapshot = updated
            onState(updated)
        }
    }
}
