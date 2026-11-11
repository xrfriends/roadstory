package com.roadstory.pleos.playback

import com.roadstory.pleos.user.ListeningFrequency
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorTest {
    @Test
    fun `file audio restores the sampled decoder position offline and clears it on completion`() {
        val file = File.createTempFile("story-audio", ".m4a")
        try {
            val asset = AudioAsset("/v1/stories/a/audio", "audio/mp4", 12, "a".repeat(64), 12_000)
            val online = speech(A).copy(localAudio = CachedStoryAudio(A, asset, file))
            val initial = Fixture(TestAudioPlayer())
            initial.coordinator.play()
            initial.detail(A, online)
            val first = checkNotNull(initial.audio).requests.single()
            initial.audio.positionMs = 0
            first.onStart(0, 12_000)
            initial.flush()
            initial.nowMs = 2_800
            initial.audio.positionMs = 2_675
            initial.coordinator.pause()
            val checkpoint = checkNotNull(initial.checkpoints.last())
            assertEquals(2_675L, checkpoint.audioPositionMs)
            assertEquals(2_800L, checkpoint.listenedMs)
            assertEquals(PlaybackPhase.PAUSED, initial.coordinator.snapshot.phase)

            val restarted = Fixture(TestAudioPlayer())
            restarted.coordinator.restoreCheckpoint(checkpoint)
            assertTrue(restarted.pendingDetails.isEmpty())
            assertEquals(2_675L, restarted.coordinator.snapshot.audioPositionMs)
            restarted.coordinator.play()
            restarted.detail(A, online.copy(text = "", offlineAudio = true))
            val audio = checkNotNull(restarted.audio)
            val resumed = audio.requests.single()
            assertEquals(2_675L, resumed.positionMs)
            audio.positionMs = 2_680
            resumed.onStart(2_680, 12_000)
            restarted.flush()
            assertTrue(restarted.coordinator.snapshot.offlineAudio)
            assertEquals(2_680L, restarted.coordinator.snapshot.audioPositionMs)
            assertTrue(restarted.started.isEmpty())
            assertTrue(restarted.player.requests.isEmpty())
            resumed.onDone()
            restarted.flush()
            assertNull(restarted.checkpoints.last())
            assertEquals(12_000L, restarted.coordinator.snapshot.audioPositionMs)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `new file digest discards old position and ignores callbacks after stop`() = with(Fixture(TestAudioPlayer())) {
        val file = File.createTempFile("story-audio", ".m4a")
        try {
            coordinator.restoreCheckpoint(PlaybackCheckpoint(A, "a".repeat(64), 0, 6_000, 5_900, 12_000))
            coordinator.play()
            val replacement = AudioAsset("/v1/stories/a/audio", "audio/mp4", 12, "b".repeat(64), 12_000)
            detail(A, speech(A).copy(localAudio = CachedStoryAudio(A, replacement, file)))
            val request = checkNotNull(audio).requests.single()
            assertEquals(0L, request.positionMs)
            assertNull(checkpoints.last())
            coordinator.stop()
            request.onStart(0, 12_000)
            request.onDone()
            flush()
            assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
            assertTrue(started.isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `manual next follows unique candidates and stops at the tail`() = with(Fixture()) {
        autoplay = false
        coordinator.startNearby()
        nearby(A, A.copy(title = "Duplicate"), QueueStory("", "Missing ID"), B)
        detail(A)
        val first = player.requests.single()
        first.start()
        flush()

        assertEquals(B, coordinator.snapshot.next)
        coordinator.next()
        assertEquals(B.id, pendingDetails.single().id)
        first.done()
        flush()
        assertEquals(PlaybackPhase.BUFFERING, coordinator.snapshot.phase)

        detail(B)
        player.requests.last().start()
        flush()
        coordinator.next()
        assertEquals(listOf(A.id, B.id), started)
        assertTrue(pendingDetails.isEmpty())
        assertNull(coordinator.snapshot.next)
        assertEquals(PlaybackPhase.PLAYING, coordinator.snapshot.phase)

        autoplay = true
        player.requests.last().done()
        flush()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertEquals(B, coordinator.snapshot.current)
        assertTrue(pendingDetails.isEmpty())
    }

    @Test
    fun `completion advances only while the current autoplay preference allows it`() = with(Fixture()) {
        coordinator.startNearby()
        nearby(A, B, C)
        detail(A)
        player.requests.last().start()
        player.requests.last().done()
        flush()
        assertEquals(B.id, pendingDetails.single().id)

        detail(B)
        autoplay = false
        player.requests.last().start()
        player.requests.last().done()
        flush()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertEquals(B, coordinator.snapshot.current)
        assertEquals(C, coordinator.snapshot.next)
        assertTrue(pendingDetails.isEmpty())
    }

    @Test
    fun `finish after current preserves narration and persists through pause and manual next`() = with(Fixture()) {
        coordinator.startNearby()
        nearby(A, B, C)
        detail(A)
        val first = player.requests.single()
        first.start()
        flush()

        coordinator.finishAfterCurrent()
        assertEquals(PlaybackPhase.PLAYING, coordinator.snapshot.phase)
        assertEquals(0, player.stops)
        coordinator.pause()
        coordinator.play()
        val replay = player.requests.last()
        assertEquals(first.text, replay.text)
        assertTrue(pendingDetails.isEmpty())
        first.done()
        flush()
        assertEquals(PlaybackPhase.BUFFERING, coordinator.snapshot.phase)
        replay.start()
        replay.done()
        flush()

        assertTrue(coordinator.snapshot.endAfterCurrent)
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertTrue(pendingDetails.isEmpty())
        coordinator.next()
        detail(B)
        player.requests.last().start()
        player.requests.last().done()
        flush()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertTrue(pendingDetails.isEmpty())

        coordinator.startNearby()
        assertFalse(coordinator.snapshot.endAfterCurrent)
    }

    @Test
    fun `account expiry lets started narration finish and clears its queued stories`() = with(Fixture()) {
        startPlan(PlaybackSource.DESTINATION)
        planned(A, B)
        detail(A)
        val narration = player.requests.single()
        narration.start()
        flush()

        coordinator.finishCurrentAfterAccountExpiry()
        assertEquals(PlaybackPhase.PLAYING, coordinator.snapshot.phase)
        assertEquals(A, coordinator.snapshot.current)
        assertTrue(coordinator.snapshot.endAfterCurrent)
        assertNull(coordinator.snapshot.source)
        assertNull(coordinator.snapshot.next)
        assertEquals(0, player.stops)

        coordinator.replaceDestinationTail(listOf(B, C))
        coordinator.next()
        assertNull(coordinator.snapshot.next)
        assertTrue(pendingDetails.isEmpty())
        narration.done()
        flush()
        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertNull(coordinator.snapshot.current)
        assertEquals(0, player.stops)
        assertEquals(listOf(A.id), started)
        assertTrue(pendingDetails.isEmpty())
    }

    @Test
    fun `account expiry cancels narration that has not started`() = with(Fixture()) {
        coordinator.playFrom(A.id)
        detail(A)
        val narration = player.requests.single()
        coordinator.finishCurrentAfterAccountExpiry()
        narration.start()
        narration.done()
        flush()

        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertEquals(1, player.stops)
        assertTrue(started.isEmpty())
    }

    @Test
    fun `expired account narration cannot resume after losing focus`() = with(Fixture()) {
        coordinator.playFrom(A.id)
        detail(A)
        val narration = player.requests.single()
        narration.start()
        flush()
        coordinator.finishCurrentAfterAccountExpiry()
        coordinator.transientFocusLoss()
        coordinator.focusGained()
        narration.done()
        flush()

        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertFalse(coordinator.snapshot.awaitingFocusGain)
        assertEquals(1, player.requests.size)
    }

    @Test
    fun `explicit selection starts a fresh session during account expiry narration`() = with(Fixture()) {
        coordinator.playFrom(A.id)
        detail(A)
        val narration = player.requests.single()
        narration.start()
        flush()
        coordinator.finishCurrentAfterAccountExpiry()
        coordinator.playFrom(B.id)
        narration.done()
        flush()
        detail(B)
        player.requests.last().start()
        player.requests.last().done()
        flush()

        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertEquals(B, coordinator.snapshot.current)
        assertFalse(coordinator.snapshot.endAfterCurrent)
        assertEquals(listOf(A.id, B.id), started)
    }

    @Test
    fun `saved or history playback loads the selected ID without acquiring a location`() = with(Fixture()) {
        coordinator.playFrom(B.id)
        assertEquals(0, pendingNearbyCount)
        assertEquals(B.id, pendingDetails.single().id)
        detail(B)
        player.requests.last().start()
        flush()
        assertEquals(PlaybackPhase.PLAYING, coordinator.snapshot.phase)
        assertEquals(PlaybackSource.SELECTED, coordinator.snapshot.source)
        assertEquals(B, coordinator.snapshot.current)
        assertNull(coordinator.snapshot.next)
        assertEquals(listOf(B.id), started)
    }

    @Test
    fun `course preserves the order of eligible stops and plays a selected row with its remaining course`() = with(Fixture()) {
        val family = C.copy(familyFriendly = true)
        val hidden = QueueStory("hidden", "Excluded topic", category = "HISTORY")
        coordinator.updateNearbyPreferences(
            NearbyStoryPreferences(
                frequencies = mapOf("HISTORY" to ListeningFrequency.OFF),
                familyMode = true,
            ),
        )
        startPlan(PlaybackSource.COURSE, B.id)
        planned(A, B, hidden, family, C)
        assertEquals(0, pendingNearbyCount)
        detail(B)
        assertEquals(PlaybackSource.COURSE, coordinator.snapshot.source)
        assertEquals(family, coordinator.snapshot.next)

        coordinator.next()
        detail(family)
        assertNull(coordinator.snapshot.next)

        startPlan(PlaybackSource.COURSE)
        planned(A, family)
        detail(A)
        assertEquals(family, coordinator.snapshot.next)
    }

    @Test
    fun `rerouting keeps the current narration and excludes previously played stories from the replacement tail`() = with(Fixture()) {
        val newStop = QueueStory("new-stop", "New route")
        startPlan(PlaybackSource.DESTINATION)
        planned(A, B, C)
        detail(A)
        player.requests.last().start()
        player.requests.last().done()
        flush()
        detail(B)
        val playing = player.requests.last()
        playing.start()
        flush()
        assertEquals(C, coordinator.snapshot.next)

        coordinator.replaceDestinationTail(emptyList())
        assertNull(coordinator.snapshot.next)
        coordinator.replaceDestinationTail(listOf(A, B, newStop))
        assertEquals(B, coordinator.snapshot.current)
        assertEquals(PlaybackPhase.PLAYING, coordinator.snapshot.phase)
        assertEquals(newStop, coordinator.snapshot.next)
        assertEquals(0, player.stops)
        playing.done()
        flush()

        detail(newStop)
        assertNull(coordinator.snapshot.next)
        assertEquals(listOf(A.id, B.id), started)
    }

    @Test
    fun `stale nearby and route results cannot replace a newer course queue`() = with(Fixture()) {
        coordinator.startNearby()
        startPlan(PlaybackSource.DESTINATION)
        startPlan(PlaybackSource.COURSE)
        nearby(A)
        planned(B)
        assertEquals(PlaybackSource.COURSE, coordinator.snapshot.source)
        assertEquals(PlaybackPhase.BUFFERING, coordinator.snapshot.phase)
        assertTrue(pendingDetails.isEmpty())

        planned(C, B)
        detail(C)
        coordinator.replaceDestinationTail(listOf(A))
        assertEquals(B, coordinator.snapshot.next)
    }

    @Test
    fun `selecting a row absent from the refreshed plan does not start another story`() = with(Fixture()) {
        startPlan(PlaybackSource.DESTINATION, B.id)
        planned(A, C)
        assertEquals(PlaybackPhase.ERROR, coordinator.snapshot.phase)
        assertNull(coordinator.snapshot.current)
        assertTrue(pendingDetails.isEmpty())
    }

    @Test
    fun `nearby failure and empty results can retry the same session without playing a fallback`() = with(Fixture()) {
        coordinator.startNearby()
        failNearby()
        assertEquals(PlaybackPhase.ERROR, coordinator.snapshot.phase)
        assertTrue(pendingDetails.isEmpty())

        coordinator.play()
        nearby()
        assertEquals(PlaybackPhase.EMPTY, coordinator.snapshot.phase)
        assertNull(coordinator.snapshot.current)
        assertTrue(player.requests.isEmpty())

        coordinator.play()
        nearby(B, C)
        assertEquals(B.id, pendingDetails.single().id)
    }

    @Test
    fun `missing or denied location has a specific issue and leaves nearby playback idle until a new request`() = with(Fixture()) {
        coordinator.startNearby()
        failNearby(CandidateUnavailable(PlaybackIssue.LOCATION_PERMISSION))
        assertEquals(PlaybackPhase.ERROR, coordinator.snapshot.phase)
        assertEquals(PlaybackIssue.LOCATION_PERMISSION, coordinator.snapshot.issue)
        assertNull(coordinator.snapshot.current)

        coordinator.startNearby()
        failNearby(CandidateUnavailable(PlaybackIssue.LOCATION_UNAVAILABLE))
        assertEquals(PlaybackPhase.ERROR, coordinator.snapshot.phase)
        assertEquals(PlaybackIssue.LOCATION_UNAVAILABLE, coordinator.snapshot.issue)
        assertTrue(pendingDetails.isEmpty())
        assertTrue(player.requests.isEmpty())

        coordinator.startNearby()
        nearby(C)
        detail(C)
        assertNull(coordinator.snapshot.issue)
    }

    @Test
    fun `pause invalidates queued detail and speech callbacks while keeping the selection`() = with(Fixture()) {
        coordinator.startNearby()
        nearby(A, B)
        pendingDetails.removeFirst().callback(Result.success(speech(A)))
        coordinator.pause()
        flush()
        assertTrue(player.requests.isEmpty())
        assertTrue(loadedStories.isEmpty())

        coordinator.play()
        detail(A)
        val request = player.requests.single()
        request.start()
        coordinator.pause()
        request.done()
        flush()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertEquals(A, coordinator.snapshot.current)
        assertTrue(started.isEmpty())
        assertTrue(pendingDetails.isEmpty())
    }

    @Test
    fun `stop invalidates candidate results and releases playback resources`() = with(Fixture()) {
        coordinator.startNearby()
        coordinator.stop()
        nearby(A, B)
        coordinator.focusGained()
        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertNull(coordinator.snapshot.next)
        assertTrue(pendingDetails.isEmpty())
        assertTrue(player.requests.isEmpty())
        assertEquals(1, player.releases)
        assertEquals(1, focusAbandons)
    }

    @Test
    fun `focus gain resumes an interrupted story until the user overrides it`() = with(Fixture()) {
        coordinator.play()
        assertEquals(0, pendingNearbyCount)
        detail(A)
        player.requests.last().start()
        flush()

        coordinator.transientFocusLoss()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertTrue(coordinator.snapshot.awaitingFocusGain)
        coordinator.focusGained()
        assertFalse(coordinator.snapshot.awaitingFocusGain)
        assertEquals(2, player.requests.size)
        assertEquals(1, focusRequests)
        player.requests.last().start()
        flush()

        coordinator.transientFocusLoss()
        coordinator.pause()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertFalse(coordinator.snapshot.awaitingFocusGain)
        coordinator.focusGained()
        assertEquals(2, player.requests.size)
        coordinator.stop()
        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
    }

    @Test
    fun `pause restarts the unfinished Korean sentence and excludes buffering and pause time`() = with(Fixture()) {
        coordinator.play()
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))
        val first = player.requests.single()
        assertEquals(FIRST_SENTENCE, first.text)
        nowMs = 2_000L
        first.start()
        flush()
        assertEquals(0L, coordinator.snapshot.listenedMs)
        assertEquals(2_000L, coordinator.snapshot.listeningSinceElapsedMs)

        nowMs = 3_200L
        first.done()
        first.done()
        flush()
        val second = player.requests.last()
        assertEquals(2, player.requests.size)
        assertEquals(SECOND_SENTENCE, second.text)
        assertEquals(PlaybackPhase.BUFFERING, coordinator.snapshot.phase)
        assertEquals(1_200L, coordinator.snapshot.listenedMs)
        assertNull(coordinator.snapshot.listeningSinceElapsedMs)

        nowMs = 4_000L
        second.start()
        flush()
        nowMs = 4_400L
        coordinator.pause()
        assertTrue(coordinator.snapshot.resumeAtSentence)
        assertEquals(1_600L, coordinator.snapshot.listenedMs)
        assertNull(coordinator.snapshot.listeningSinceElapsedMs)

        nowMs = 9_900L
        coordinator.play()
        second.done()
        first.error("Old utterance")
        flush()
        assertEquals(1_600L, coordinator.snapshot.listenedMs)
        assertEquals(PlaybackPhase.BUFFERING, coordinator.snapshot.phase)
        val resumed = player.requests.last()
        assertEquals(3, player.requests.size)
        assertEquals(SECOND_SENTENCE, resumed.text)
        resumed.start()
        flush()
        assertEquals(9_900L, coordinator.snapshot.listeningSinceElapsedMs)

        nowMs = 11_600L
        resumed.done()
        flush()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertFalse(coordinator.snapshot.resumeAtSentence)
        assertEquals(3_300L, coordinator.snapshot.listenedMs)
        assertNull(coordinator.snapshot.listeningSinceElapsedMs)
        assertEquals(listOf(A.id), started)
        assertTrue(pendingDetails.isEmpty())

        coordinator.play()
        assertEquals(FIRST_SENTENCE, player.requests.last().text)
        assertEquals(0L, coordinator.snapshot.listenedMs)
        player.requests.last().start()
        flush()
        assertEquals(listOf(A.id, A.id), started)
    }

    @Test
    fun `focus restores the unfinished sentence and suspend waits for explicit play`() = with(Fixture()) {
        coordinator.play()
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))
        player.requests.single().run {
            start()
            done()
        }
        flush()
        val interrupted = player.requests.last()
        assertEquals(SECOND_SENTENCE, interrupted.text)
        interrupted.start()
        flush()

        coordinator.transientFocusLoss()
        assertTrue(coordinator.snapshot.awaitingFocusGain)
        coordinator.focusGained()
        assertEquals(SECOND_SENTENCE, player.requests.last().text)
        assertEquals(1, focusRequests)
        player.requests.last().start()
        flush()

        coordinator.transientFocusLoss()
        coordinator.suspendPlayback()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertTrue(coordinator.snapshot.resumeAtSentence)
        assertFalse(coordinator.snapshot.awaitingFocusGain)
        coordinator.focusGained()
        assertEquals(3, player.requests.size)

        coordinator.play()
        assertEquals(SECOND_SENTENCE, player.requests.last().text)
        assertEquals(2, focusRequests)
        player.requests.last().start()
        flush()
        assertEquals(listOf(A.id), started)
        coordinator.stop()
        player.requests.last().done()
        flush()
        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertEquals(0L, coordinator.snapshot.listenedMs)
        assertFalse(coordinator.snapshot.resumeAtSentence)
        coordinator.play()
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))
        assertEquals(FIRST_SENTENCE, player.requests.last().text)
    }

    @Test
    fun `suspend cancels preparation that has not started any narration`() = with(Fixture()) {
        coordinator.play()
        coordinator.suspendPlayback()
        detail(A)
        coordinator.focusGained()

        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertNull(coordinator.snapshot.current)
        assertTrue(player.requests.isEmpty())
        assertTrue(started.isEmpty())
    }

    @Test
    fun `restart waits for play and resumes the saved sentence without repeating listening history`() = with(Fixture()) {
        coordinator.play()
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))
        player.requests.single().start()
        flush()
        assertEquals(0, checkpoints.single()?.sentenceIndex)

        nowMs = 1_200L
        player.requests.single().done()
        flush()
        assertEquals(1, checkpoints.last()?.sentenceIndex)
        assertEquals(1_200L, checkpoints.last()?.listenedMs)
        player.requests.last().start()
        flush()
        nowMs = 1_600L
        assertEquals(1_600L, coordinator.checkpointForRestart()?.listenedMs)

        coordinator.close()
        val saved = checkNotNull(checkpoints.last())
        assertEquals(1_600L, saved.listenedMs)

        with(Fixture()) {
            coordinator.restoreCheckpoint(saved)
            coordinator.suspendPlayback()
            coordinator.focusGained()
            assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
            assertEquals(A, coordinator.snapshot.current)
            assertEquals(1, coordinator.snapshot.sentenceIndex)
            assertEquals(1_600L, coordinator.snapshot.listenedMs)
            assertTrue(coordinator.snapshot.resumeAtSentence)
            assertTrue(checkpoints.isEmpty())
            assertTrue(pendingDetails.isEmpty())
            assertTrue(player.requests.isEmpty())
            assertEquals(0, focusRequests)

            coordinator.play()
            detail(A, response = speech(A).copy(text = "$FIRST_SENTENCE   $SECOND_SENTENCE"))
            assertEquals(SECOND_SENTENCE, player.requests.single().text)
            player.requests.single().start()
            flush()
            assertEquals(1, coordinator.snapshot.sentenceIndex)
            assertTrue(started.isEmpty())
            player.requests.single().done()
            flush()
            assertEquals(listOf<PlaybackCheckpoint?>(null), checkpoints)
            assertFalse(coordinator.snapshot.resumeAtSentence)
            assertEquals(-1, coordinator.snapshot.sentenceIndex)
        }
    }

    @Test
    fun `retry preserves a restored checkpoint until the current narration can be checked`() = with(Fixture()) {
        val saved = secondSentenceCheckpoint()
        coordinator.restoreCheckpoint(saved)
        coordinator.play()
        pendingDetails.removeFirst().callback(Result.failure(IllegalStateException("Unavailable")))
        flush()
        assertEquals(PlaybackPhase.ERROR, coordinator.snapshot.phase)
        assertEquals(saved, coordinator.checkpointForRestart())
        assertTrue(checkpoints.isEmpty())

        coordinator.play()
        coordinator.suspendPlayback()
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertEquals(saved, coordinator.checkpointForRestart())
        assertTrue(player.requests.isEmpty())

        coordinator.play()
        detail(A, response = speech(A).copy(text = "$FIRST_SENTENCE 수정한 두 번째 문장입니다!"))
        assertEquals(FIRST_SENTENCE, player.requests.single().text)
        assertEquals(0L, coordinator.snapshot.listenedMs)
        assertEquals(listOf<PlaybackCheckpoint?>(null), checkpoints)
        player.requests.single().start()
        flush()
        assertEquals(listOf(A.id), started)
        assertEquals(0, checkpoints.last()?.sentenceIndex)
        assertFalse(saved.narrationFingerprint == checkpoints.last()?.narrationFingerprint)
    }

    @Test
    fun `restoring an owner cancels outgoing detail and explicit selection clears the restored checkpoint`() = with(Fixture()) {
        val saved = secondSentenceCheckpoint()
        coordinator.playFrom(B.id)
        coordinator.restoreCheckpoint(saved)
        detail(B)
        assertEquals(A, coordinator.snapshot.current)
        assertTrue(player.requests.isEmpty())
        assertTrue(checkpoints.isEmpty())

        coordinator.playFrom(A.id)
        assertEquals(listOf<PlaybackCheckpoint?>(null), checkpoints)
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))
        assertEquals(FIRST_SENTENCE, player.requests.single().text)

        coordinator.restoreCheckpoint(saved)
        coordinator.stop()
        assertEquals(listOf<PlaybackCheckpoint?>(null, null), checkpoints)
        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertNull(coordinator.checkpointForRestart())
    }

    @Test
    fun `out of range sentence starts the same verified narration from the first sentence`() = with(Fixture()) {
        coordinator.restoreCheckpoint(secondSentenceCheckpoint().copy(sentenceIndex = 2))
        coordinator.play()
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))

        assertEquals(FIRST_SENTENCE, player.requests.single().text)
        assertEquals(0, coordinator.snapshot.sentenceIndex)
        assertEquals(listOf<PlaybackCheckpoint?>(null), checkpoints)
    }

    @Test
    fun `expiry between sentences finishes the current public story without advancing`() = with(Fixture()) {
        coordinator.startNearby()
        nearby(A, B)
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))
        player.requests.single().start()
        player.requests.single().done()
        flush()
        assertEquals(PlaybackPhase.BUFFERING, coordinator.snapshot.phase)
        coordinator.finishCurrentAfterAccountExpiry()
        assertEquals(PlaybackPhase.BUFFERING, coordinator.snapshot.phase)
        assertEquals(A, coordinator.snapshot.current)
        assertEquals(SECOND_SENTENCE, player.requests.last().text)
        assertNull(coordinator.snapshot.next)

        player.requests.last().run {
            start()
            done()
        }
        flush()
        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertNull(coordinator.snapshot.current)
        assertTrue(pendingDetails.isEmpty())
        assertEquals(listOf(A.id), started)
    }

    @Test
    fun `duplicate start and stale completion cannot record listening twice or move the queue`() = with(Fixture()) {
        coordinator.startNearby()
        nearby(A, B, C)
        detail(A)
        val first = player.requests.single()
        first.start()
        first.start()
        first.done()
        first.done()
        flush()
        assertEquals(listOf(A.id), started)
        assertEquals(B.id, pendingDetails.single().id)
        assertEquals(C, coordinator.snapshot.next)
    }

    @Test
    fun `completion before narration starts reports an error and retains manual next`() = with(Fixture()) {
        coordinator.startNearby()
        nearby(A, B)
        detail(A)
        val request = player.requests.single()
        request.done()
        request.start()
        flush()

        assertEquals(PlaybackPhase.ERROR, coordinator.snapshot.phase)
        assertEquals(B, coordinator.snapshot.next)
        assertTrue(started.isEmpty())
        assertTrue(pendingDetails.isEmpty())
    }

    @Test
    fun `old synthesis error is ignored and current synthesis error never advances the queue`() = with(Fixture()) {
        coordinator.startNearby()
        nearby(A, B, C)
        detail(A)
        val first = player.requests.single()
        coordinator.next()
        detail(B)
        val second = player.requests.last()

        first.error("Late callback")
        flush()
        assertEquals(PlaybackPhase.BUFFERING, coordinator.snapshot.phase)
        second.start()
        second.error("Current failure")
        second.done()
        flush()

        assertEquals(PlaybackPhase.ERROR, coordinator.snapshot.phase)
        assertEquals(B, coordinator.snapshot.current)
        assertEquals(C, coordinator.snapshot.next)
        assertTrue(pendingDetails.isEmpty())
        assertEquals(listOf(B.id), started)
    }

    @Test
    fun `adapter cleanup errors cannot prevent stop or enable a late resume`() = with(Fixture()) {
        coordinator.startNearby()
        nearby(A, B)
        detail(A)
        val request = player.requests.single()
        request.start()
        flush()
        player.failCleanup = true

        coordinator.stop()
        request.done()
        flush()
        coordinator.focusGained()

        assertEquals(PlaybackPhase.STOPPED, coordinator.snapshot.phase)
        assertNull(coordinator.snapshot.next)
        assertEquals(1, player.stops)
        assertEquals(1, player.releases)
        assertEquals(1, focusAbandons)
        assertTrue(pendingDetails.isEmpty())
    }

    @Test
    fun `focus acquisition exception is recoverable through explicit replay`() = with(Fixture()) {
        failFocus = true
        coordinator.startNearby()
        assertEquals(PlaybackPhase.ERROR, coordinator.snapshot.phase)
        assertTrue(pendingDetails.isEmpty())

        failFocus = false
        coordinator.play()
        nearby(B)
        assertEquals(B.id, pendingDetails.single().id)
    }

    @Test
    fun `family priority preserves server order among ties and never restores excluded stories`() = with(Fixture()) {
        val often = A.copy(category = "INDUSTRY")
        val firstFamily = B.copy(familyFriendly = true)
        val secondFamily = C.copy(category = "DAILY_LIFE", familyFriendly = true)
        val off = QueueStory("off", "Disabled topic", category = "HISTORY", familyFriendly = true)
        val disliked = QueueStory("disliked", "Disliked story", familyFriendly = true)
        coordinator.updateNearbyPreferences(
            NearbyStoryPreferences(
                frequencies = mapOf("INDUSTRY" to ListeningFrequency.OFTEN, "HISTORY" to ListeningFrequency.OFF),
                familyMode = true,
                dislikedStoryIds = setOf(disliked.id),
            ),
        )
        coordinator.startNearby()
        nearby(often, off, firstFamily, disliked, secondFamily)
        detail(firstFamily)
        assertEquals(secondFamily, coordinator.snapshot.next)
        coordinator.next()
        detail(secondFamily)
        assertEquals(often, coordinator.snapshot.next)
        coordinator.next()
        detail(often)
        assertNull(coordinator.snapshot.next)
    }

    @Test
    fun `latest topic preferences apply when pending nearby candidates arrive`() = with(Fixture()) {
        coordinator.startNearby()
        coordinator.updateNearbyPreferences(
            NearbyStoryPreferences(frequencies = mapOf("INDUSTRY" to ListeningFrequency.OFTEN)),
        )
        val industry = B.copy(category = "INDUSTRY")
        nearby(A, industry, C)
        detail(industry)
        assertEquals(industry, coordinator.snapshot.current)
        assertEquals(A, coordinator.snapshot.next)
    }

    @Test
    fun `older detail payload keeps the nearby category for current story feedback`() = with(Fixture()) {
        val industry = A.copy(category = "INDUSTRY")
        coordinator.startNearby()
        nearby(industry)
        detail(industry, response = speech(A))

        assertEquals(industry, coordinator.snapshot.current)
        assertEquals("INDUSTRY", loadedStories.single().category)
    }

    @Test
    fun `dislike keeps the current story and demotes its topic for both next commands`() = with(Fixture()) {
        val current = A.copy(category = "INDUSTRY")
        val similar = B.copy(category = "INDUSTRY")
        val preferences = NearbyStoryPreferences(frequencies = mapOf("INDUSTRY" to ListeningFrequency.OFTEN))
        coordinator.updateNearbyPreferences(preferences)
        coordinator.startNearby()
        nearby(current, similar, C)
        detail(current)
        val first = player.requests.single()
        first.start()
        flush()
        assertEquals(similar, coordinator.snapshot.next)

        coordinator.updateNearbyPreferences(
            preferences.copy(dislikedStoryIds = setOf(current.id), dislikedCategories = setOf("INDUSTRY")),
        )
        assertEquals(current, coordinator.snapshot.current)
        assertEquals(C, coordinator.snapshot.next)
        assertEquals(PlaybackPhase.PLAYING, coordinator.snapshot.phase)
        assertEquals(0, player.stops)
        first.done()
        flush()

        detail(C)
        coordinator.next()
        detail(similar)
        assertNull(coordinator.snapshot.next)
    }

    @Test
    fun `topic toggles restore unplayed candidates without replacing in-flight or selected stories`() = with(Fixture()) {
        val industry = A.copy(category = "INDUSTRY")
        val dailyLife = B.copy(category = "DAILY_LIFE")
        coordinator.startNearby()
        nearby(industry, dailyLife, C)

        coordinator.updateNearbyPreferences(
            NearbyStoryPreferences(
                frequencies = mapOf("INDUSTRY" to ListeningFrequency.OFF, "DAILY_LIFE" to ListeningFrequency.OFF),
            ),
        )
        assertEquals(industry, coordinator.snapshot.current)
        assertEquals(C, coordinator.snapshot.next)
        detail(industry)
        coordinator.next()
        detail(C)

        coordinator.updateNearbyPreferences(NearbyStoryPreferences())
        assertEquals(C, coordinator.snapshot.current)
        assertEquals(dailyLife, coordinator.snapshot.next)
        coordinator.next()
        detail(dailyLife)
        assertNull(coordinator.snapshot.next)
    }

    @Test
    fun `all excluded is distinct from no candidates and allows an explicit retry or story selection`() = with(Fixture()) {
        val industry = A.copy(category = "INDUSTRY")
        val excluded = NearbyStoryPreferences(
            frequencies = mapOf("INDUSTRY" to ListeningFrequency.OFF),
            dislikedStoryIds = setOf(B.id),
        )
        coordinator.updateNearbyPreferences(excluded)
        coordinator.startNearby()
        nearby(industry, B)
        assertEquals(PlaybackPhase.EMPTY, coordinator.snapshot.phase)
        assertTrue(coordinator.snapshot.filteredByPreferences)
        assertTrue(pendingDetails.isEmpty())

        coordinator.updateNearbyPreferences(NearbyStoryPreferences())
        assertEquals(PlaybackPhase.EMPTY, coordinator.snapshot.phase)
        assertTrue(pendingDetails.isEmpty())
        coordinator.play()
        nearby(industry, B)
        detail(industry)

        coordinator.updateNearbyPreferences(excluded)
        coordinator.playFrom(B.id)
        detail(B)
        assertNull(coordinator.snapshot.next)
        assertEquals(0, pendingNearbyCount)
        assertFalse(coordinator.snapshot.filteredByPreferences)

        coordinator.startNearby()
        nearby()
        assertEquals(PlaybackPhase.EMPTY, coordinator.snapshot.phase)
        assertFalse(coordinator.snapshot.filteredByPreferences)
    }

    @Test
    fun `filtering the entire remaining queue prevents automatic and manual advances`() = with(Fixture()) {
        val disabled = B.copy(category = "DAILY_LIFE")
        coordinator.startNearby()
        nearby(A, disabled)
        detail(A)
        val first = player.requests.single()
        first.start()
        flush()
        coordinator.updateNearbyPreferences(
            NearbyStoryPreferences(frequencies = mapOf("DAILY_LIFE" to ListeningFrequency.OFF)),
        )
        assertNull(coordinator.snapshot.next)
        coordinator.next()
        first.done()
        flush()
        assertEquals(PlaybackPhase.PAUSED, coordinator.snapshot.phase)
        assertTrue(pendingDetails.isEmpty())
        assertEquals(listOf(A.id), started)
    }

    private class Fixture(val audio: TestAudioPlayer? = null) {
        private val dispatchQueue = ArrayDeque<() -> Unit>()
        private val pendingNearby = ArrayDeque<(Result<List<QueueStory>>) -> Unit>()
        private val pendingPlans = ArrayDeque<(Result<List<QueueStory>>) -> Unit>()
        val pendingNearbyCount get() = pendingNearby.size
        val pendingDetails = ArrayDeque<DetailRequest>()
        val player = TestSpeechPlayer()
        val loadedStories = mutableListOf<StorySpeech>()
        val started = mutableListOf<String>()
        val checkpoints = mutableListOf<PlaybackCheckpoint?>()
        var autoplay = true
        var failFocus = false
        var focusRequests = 0
        var focusAbandons = 0
        var nowMs = 0L

        val coordinator = PlaybackCoordinator(
            loadNearby = { pendingNearby.addLast(it) },
            loadStory = { id, callback -> pendingDetails.addLast(DetailRequest(id, callback)) },
            player = player,
            requestFocus = {
                focusRequests++
                check(!failFocus)
                true
            },
            abandonFocus = { focusAbandons++ },
            autoplayEnabled = { autoplay },
            initialStory = { A },
            dispatch = { dispatchQueue.addLast(it) },
            onState = {},
            onStoryLoaded = { loadedStories += it },
            onStoryStarted = { started += it.id },
            clock = { nowMs },
            onCheckpointChanged = { checkpoints += it },
            audioPlayer = audio,
        )

        fun nearby(vararg stories: QueueStory) {
            pendingNearby.removeFirst()(Result.success(stories.toList()))
            flush()
        }

        fun failNearby(cause: Throwable = IllegalStateException("Unavailable")) {
            pendingNearby.removeFirst()(Result.failure(cause))
            flush()
        }

        fun startPlan(source: PlaybackSource, selectedId: String? = null) =
            coordinator.startPlan(source, selectedId) { pendingPlans.addLast(it) }

        fun planned(vararg stories: QueueStory) {
            pendingPlans.removeFirst()(Result.success(stories.toList()))
            flush()
        }

        fun detail(story: QueueStory, response: StorySpeech = speech(story)) {
            val request = pendingDetails.removeFirst()
            assertEquals(story.id, request.id)
            request.callback(Result.success(response))
            flush()
        }

        fun flush() {
            while (dispatchQueue.isNotEmpty()) dispatchQueue.removeFirst()()
        }
    }

    private data class DetailRequest(val id: String, val callback: (Result<StorySpeech>) -> Unit)

    private class TestAudioPlayer : StoryAudioPlayer {
        data class Request(val positionMs: Long, val onStart: (Long, Long) -> Unit, val onDone: () -> Unit)

        val requests = mutableListOf<Request>()
        override var positionMs: Long? = null

        override fun play(
            file: File, startPositionMs: Long, onStart: (Long, Long) -> Unit, onDone: () -> Unit, onError: () -> Unit,
        ) {
            requests += Request(startPositionMs, onStart, onDone)
        }

        override fun stop() {
            positionMs = null
        }
    }

    private class TestSpeechPlayer : StorySpeechPlayer {
        data class Request(val text: String, val start: () -> Unit, val done: () -> Unit, val error: (String) -> Unit)

        val requests = mutableListOf<Request>()
        var stops = 0
        var releases = 0
        var failCleanup = false
        override val isPlaying: Boolean get() = requests.isNotEmpty()

        override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
            requests += Request(text, onStart, onDone, onError)
        }

        override fun stop() {
            stops++
            check(!failCleanup)
        }

        override fun release() {
            releases++
            check(!failCleanup)
        }
    }

    private fun secondSentenceCheckpoint(): PlaybackCheckpoint = with(Fixture()) {
        coordinator.play()
        detail(A, response = speech(A).copy(text = KOREAN_NARRATION))
        player.requests.single().start()
        flush()
        nowMs = 1_000L
        player.requests.single().done()
        flush()
        checkNotNull(checkpoints.last())
    }

    private companion object {
        val A = QueueStory("a", "First", "Seongsu", 12)
        val B = QueueStory("b", "Second", "Seongsu", 13)
        val C = QueueStory("c", "Third", "Seongsu", 14)
        const val FIRST_SENTENCE = "성수동에는 오래된 공장이 있습니다."
        const val SECOND_SENTENCE = "오늘은 그 이야기를 들려드립니다!"
        const val KOREAN_NARRATION = "$FIRST_SENTENCE $SECOND_SENTENCE"

        fun speech(story: QueueStory) = StorySpeech(
            id = story.id,
            title = story.title,
            text = "Narration ${story.id}",
            placeName = story.placeName,
            durationSeconds = story.durationSeconds,
            category = story.category,
            familyFriendly = story.familyFriendly,
        )
    }
}
