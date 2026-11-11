package com.roadstory.pleos.user

import java.io.IOException
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncCoordinatorTest {
    @Test
    fun offlineWritesSurviveRestartAndRetryOnlyForTheirOwner() {
        val test = Harness()
        test.signIn("alpha")
        test.transport.failure = IOException()
        assertEquals(true, test.sync.toggle(LibraryKind.STORY, story))
        test.settle()
        assertEquals(LibrarySyncMode.OFFLINE, test.sync.status().mode)
        assertEquals(listOf(story), test.sync.visibleLibrary()?.savedStories)
        assertEquals(1, test.persistence.load("alpha").pending.size)
        assertEquals(listOf(5_000L), test.retries.map { it.first })

        test.transport.failure = null
        val restarted = test.restart()
        restarted.signIn("beta")
        assertEquals(emptyList<LibraryItem>(), restarted.sync.visibleLibrary()?.savedStories)
        assertEquals(1, restarted.persistence.load("alpha").pending.size)
        assertEquals(0, restarted.sync.status().pendingCount)

        restarted.signIn("alpha")
        assertEquals(listOf(story), restarted.sync.visibleLibrary()?.savedStories)
        assertEquals(0, restarted.sync.status().pendingCount)
        assertEquals(listOf("alpha"), restarted.transport.completedWrites.map { it.first })
    }

    @Test
    fun delayedAcknowledgementDoesNotClearNewerLocalIntent() {
        val test = Harness()
        test.signIn("alpha")
        test.sync.toggle(LibraryKind.STORY, story)
        test.network.removeFirst().invoke()
        // The first request already reached the server while the UI still awaits its completion.
        assertEquals(false, test.sync.toggle(LibraryKind.STORY, story))
        assertEquals(emptyList<LibraryItem>(), test.sync.visibleLibrary()?.savedStories)

        test.completions.removeFirst().invoke()
        assertEquals(1, test.sync.status().pendingCount)
        assertEquals(emptyList<LibraryItem>(), test.sync.visibleLibrary()?.savedStories)
        test.settle()
        assertEquals(listOf(true, false), test.transport.completedWrites.map { it.second.saved })
        assertEquals(LibrarySyncMode.SYNCED, test.sync.status().mode)
        assertEquals(emptyList<LibraryItem>(), test.sync.visibleLibrary()?.savedStories)
    }

    @Test
    fun signOutImmediatelyHidesAccountAndIgnoresLateResponse() {
        val test = Harness()
        test.transport.remote["alpha"] = LibraryContents(savedStories = listOf(story))
        test.sync.signIn("alpha")
        test.network.removeFirst().invoke()
        test.sync.signOut()
        test.signIn("beta")

        assertEquals("beta", test.sync.status().accountId)
        assertEquals(emptyList<LibraryItem>(), test.sync.visibleLibrary()?.savedStories)
        assertNull(test.sync.currentStory())
        assertTrue(test.transport.completedWrites.isEmpty())
        test.sync.signOut()
        assertEquals(LibrarySyncMode.GUEST, test.sync.status().mode)
        assertNull(test.sync.visibleLibrary())
    }

    @Test
    fun lateWriteCompletionCannotModifyNextAccountOrConsumeItsQueue() {
        val test = Harness()
        test.signIn("alpha")
        test.sync.toggle(LibraryKind.STORY, story)
        test.network.removeFirst().invoke()

        test.sync.signOut()
        test.signIn("beta")
        test.sync.toggle(LibraryKind.COURSE, course)
        test.settle()

        assertEquals(emptyList<LibraryItem>(), test.sync.visibleLibrary()?.savedStories)
        assertEquals(listOf(course), test.sync.visibleLibrary()?.savedCourses)
        assertEquals(1, test.persistence.load("alpha").pending.size)
        assertEquals(listOf("alpha", "beta"), test.transport.completedWrites.map { it.first })
    }

    @Test
    fun importingBookmarksChecksTheConfirmedOwnerAndSkipsDuplicates() {
        val test = Harness()
        test.signIn("alpha")
        assertEquals(0, test.sync.importBookmarks("beta", listOf(story), listOf(course)))
        assertEquals(2, test.sync.importBookmarks("alpha", listOf(story, story), listOf(course)))
        assertEquals(0, test.sync.importBookmarks("alpha", listOf(story), listOf(course)))
        test.settle()

        assertEquals(listOf(story), test.sync.visibleLibrary()?.savedStories)
        assertEquals(listOf(course), test.sync.visibleLibrary()?.savedCourses)
        assertEquals(emptyList<ListeningRecord>(), test.sync.visibleLibrary()?.history)
        assertEquals(listOf(LibraryKind.STORY, LibraryKind.COURSE), test.transport.completedWrites.map { it.second.kind })
    }

    @Test
    fun expiredSessionRetainsPendingOperationWithoutExposingAccount() {
        val test = Harness()
        test.signIn("alpha")
        test.transport.failure = LibraryAuthenticationRequired()
        test.sync.recordListening(story)
        test.settle()

        assertEquals(LibrarySyncMode.SIGN_IN_REQUIRED, test.sync.status().mode)
        assertNull(test.sync.visibleLibrary())
        assertNull(test.sync.currentStory())
        assertEquals(LibraryKind.HISTORY, test.persistence.load("alpha").pending.single().kind)

        test.transport.failure = null
        test.signIn("alpha")
        assertEquals(story, test.sync.visibleLibrary()?.history?.single()?.item)
        assertEquals("alpha", test.transport.completedWrites.single().first)
    }

    @Test
    fun rejectedSaveRollsBackWhileOtherChangesSynchronize() {
        val test = Harness()
        test.signIn("alpha")
        test.transport.rejectStory = true
        test.sync.toggle(LibraryKind.STORY, story)
        test.sync.toggle(LibraryKind.COURSE, course)
        test.settle()

        assertEquals(emptyList<LibraryItem>(), test.sync.visibleLibrary()?.savedStories)
        assertEquals(listOf(course), test.sync.visibleLibrary()?.savedCourses)
        assertEquals(1, test.sync.status().rejectedCount)
        assertEquals(0, test.sync.status().pendingCount)
    }

    @Test
    fun accountReturnedForRefreshMustMatchVerifiedSession() {
        val test = Harness()
        test.signIn("alpha")
        test.transport.nextAccountId = "beta"
        test.sync.syncNow()
        test.settle()

        assertEquals(LibrarySyncMode.SIGN_IN_REQUIRED, test.sync.status().mode)
        assertNull(test.sync.visibleLibrary())
        assertTrue(test.transport.completedWrites.isEmpty())
    }

    @Test
    fun cachedOutboxPreservesPlayedAtAndNewestHistory() {
        val time = Instant.parse("2026-11-11T01:42:30Z")
        val newer = LibraryMutation(LibraryKind.HISTORY, story, playedAt = time.plusSeconds(1))
        val older = LibraryMutation(LibraryKind.HISTORY, story, playedAt = time)
        val initial = AccountLibrary(
            snapshot = newer.applyTo(LibraryContents()),
            pending = listOf(older),
            currentStory = story,
        )
        val loaded = LibraryJson.readCache(JSONObject(LibraryJson.writeCache(initial).toString()))

        assertEquals(initial, loaded)
        assertEquals(time.plusSeconds(1), loaded.visible().history.single().playedAt)
    }

    @Test
    fun developmentApiOnlyAcceptsExplicitLocalOrigins() {
        assertTrue(isLocalApi("http://10.0.2.2:8080"))
        assertTrue(isLocalApi("http://127.0.0.1:8080/"))
        assertTrue(isLocalApi("https://localhost"))
        assertFalse(isLocalApi("http://10.0.2.2.example.test:8080"))
        assertFalse(isLocalApi("http://10.0.2.2@example.test"))
        assertFalse(isLocalApi("http://10.0.2.2:8080/account"))
        assertFalse(isLocalApi("http://10.0.2.2:8080?redirect=elsewhere"))
    }

    private class Harness(
        val transport: TestTransport = TestTransport(),
        val persistence: TestPersistence = TestPersistence(),
    ) {
        val network = ArrayDeque<() -> Unit>()
        val completions = ArrayDeque<() -> Unit>()
        val retries = ArrayDeque<Pair<Long, () -> Unit>>()
        val sync = LibrarySyncCoordinator(
            transport, persistence, { network.addLast(it) }, { completions.addLast(it) },
            { delay, task -> retries.addLast(delay to task) }, {},
        )

        fun signIn(account: String) {
            sync.signIn(account)
            settle()
        }

        fun settle() {
            while (network.isNotEmpty() || completions.isNotEmpty()) {
                network.removeFirstOrNull()?.invoke()
                completions.removeFirstOrNull()?.invoke()
            }
        }

        fun restart() = Harness(transport, persistence)
    }

    private class TestTransport : LibraryTransport {
        val remote = mutableMapOf<String, LibraryContents>()
        val completedWrites = mutableListOf<Pair<String, LibraryMutation>>()
        var failure: Exception? = null
        var rejectStory = false
        var nextAccountId: String? = null

        override fun fetch(bearer: String): VerifiedLibrary {
            failure?.let { throw it }
            val returnedAccount = nextAccountId ?: bearer
            nextAccountId = null
            return VerifiedLibrary(returnedAccount, remote[returnedAccount] ?: LibraryContents())
        }

        override fun write(bearer: String, mutation: LibraryMutation) {
            failure?.let { throw it }
            if (rejectStory && mutation.kind == LibraryKind.STORY) throw LibraryMutationRejected()
            remote[bearer] = mutation.applyTo(remote[bearer] ?: LibraryContents())
            completedWrites.add(bearer to mutation)
        }
    }

    private class TestPersistence : LibraryPersistence {
        private val stores = mutableMapOf<String, String>()

        override fun load(accountId: String): AccountLibrary =
            stores[accountId]?.let { LibraryJson.readCache(JSONObject(it)) } ?: AccountLibrary()

        override fun save(accountId: String, library: AccountLibrary) {
            stores[accountId] = LibraryJson.writeCache(library).toString()
        }
    }

    private companion object {
        val story = LibraryItem("story-published-1", "이야기", category = "HISTORY")
        val course = LibraryItem("course-seongsu", "코스")
    }
}
