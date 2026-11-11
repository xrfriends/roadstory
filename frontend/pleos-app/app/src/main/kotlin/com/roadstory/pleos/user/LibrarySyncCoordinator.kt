package com.roadstory.pleos.user

import java.time.Instant
import java.util.UUID

enum class LibrarySyncMode { GUEST, SIGNING_IN, SYNCED, PENDING, OFFLINE, SIGN_IN_REQUIRED }

data class LibrarySyncStatus(
    val mode: LibrarySyncMode,
    val accountId: String? = null,
    val pendingCount: Int = 0,
    val rejectedCount: Int = 0,
)

internal data class ListeningRecord(val item: LibraryItem, val playedAt: Instant)

internal data class LibraryContents(
    val savedStories: List<LibraryItem> = emptyList(),
    val savedCourses: List<LibraryItem> = emptyList(),
    val history: List<ListeningRecord> = emptyList(),
)

internal data class VerifiedLibrary(val accountId: String, val contents: LibraryContents)

internal enum class LibraryKind { STORY, COURSE, HISTORY }

internal data class LibraryMutation(
    val kind: LibraryKind,
    val item: LibraryItem,
    val saved: Boolean = true,
    val playedAt: Instant? = null,
    val revision: String = UUID.randomUUID().toString(),
) {
    val key get() = kind to item.id

    fun applyTo(contents: LibraryContents): LibraryContents {
        return when (kind) {
            LibraryKind.STORY -> contents.copy(savedStories = updateSaved(contents.savedStories))
            LibraryKind.COURSE -> contents.copy(savedCourses = updateSaved(contents.savedCourses))
            LibraryKind.HISTORY -> {
                val previous = contents.history.find { it.item.id == item.id }
                val time = checkNotNull(playedAt)
                if (previous != null && previous.playedAt >= time) return contents
                val updated = contents.history.filterNot { it.item.id == item.id } + ListeningRecord(item, time)
                contents.copy(history = updated.sortedByDescending { it.playedAt }.take(100))
            }
        }
    }

    private fun updateSaved(items: List<LibraryItem>): List<LibraryItem> {
        val others = items.filterNot { it.id == item.id }
        return if (saved) listOf(item) + others else others
    }
}

internal data class AccountLibrary(
    val snapshot: LibraryContents = LibraryContents(),
    val pending: List<LibraryMutation> = emptyList(),
    val currentStory: LibraryItem? = null,
) {
    fun visible(): LibraryContents = pending.fold(snapshot) { library, mutation -> mutation.applyTo(library) }

    fun queue(mutation: LibraryMutation): AccountLibrary =
        copy(pending = pending.filterNot { it.key == mutation.key } + mutation)
}

internal interface LibraryPersistence {
    fun load(accountId: String): AccountLibrary
    fun save(accountId: String, library: AccountLibrary)
}

internal interface LibraryTransport {
    fun fetch(bearer: String): VerifiedLibrary
    fun write(bearer: String, mutation: LibraryMutation)
}

internal class LibraryAuthenticationRequired : Exception()
internal class LibraryMutationRejected : Exception()

/** State transitions and completion delivery use the UI dispatcher. HTTP never runs on that dispatcher. */
internal class LibrarySyncCoordinator(
    private val transport: LibraryTransport,
    private val persistence: LibraryPersistence,
    private val execute: (() -> Unit) -> Unit,
    private val dispatch: (() -> Unit) -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val changed: () -> Unit,
    private val now: () -> Instant = Instant::now,
) {
    private class Session(val accountId: String, val bearer: String)

    private var session: Session? = null
    private var account = AccountLibrary()
    private var visible = LibraryContents()
    private var mode = LibrarySyncMode.GUEST
    private var epoch = 0L
    private var busy = false
    private var refreshRequested = false
    private var retryTicket = 0L
    private var consecutiveFailures = 0
    private var rejectedCount = 0

    @Synchronized
    fun status() = LibrarySyncStatus(mode, session?.accountId, account.pending.size, rejectedCount)

    @Synchronized
    fun visibleLibrary(): LibraryContents? = if (session == null) null else visible

    @Synchronized
    fun currentStory(): LibraryItem? =
        account.currentStory ?: visible.history.firstOrNull()?.item

    @Synchronized
    fun setCurrentStory(item: LibraryItem): Boolean {
        if (session == null) return false
        if (account.currentStory != item) {
            updateAccount(account.copy(currentStory = item))
            changed()
        }
        return true
    }

    @Synchronized
    fun signIn(bearer: String) {
        reset(LibrarySyncMode.SIGNING_IN)
        val attempt = epoch
        changed()
        if (bearer.isBlank() || bearer.length > 8192 || bearer.any { it !in '!'..'~' }) {
            completeSignIn(attempt, bearer, Result.failure(LibraryAuthenticationRequired()))
            return
        }
        execute {
            if (isCurrent(attempt)) {
                val result = runCatching { transport.fetch(bearer) }
                dispatch { completeSignIn(attempt, bearer, result) }
            }
        }
    }

    @Synchronized
    fun signOut() {
        reset(LibrarySyncMode.GUEST)
        changed()
    }

    @Synchronized
    fun syncNow() {
        if (session == null) return
        refreshRequested = true
        retryTicket++
        consecutiveFailures = 0
        drain()
    }

    @Synchronized
    fun toggle(kind: LibraryKind, item: LibraryItem): Boolean? {
        if (session == null) return null
        val items = when (kind) {
            LibraryKind.STORY -> visible.savedStories
            LibraryKind.COURSE -> visible.savedCourses
            LibraryKind.HISTORY -> error("Use recordListening for history")
        }
        val saving = items.none { it.id == item.id }
        updateAccount(account.queue(LibraryMutation(kind, item, saved = saving)))
        onLocalWrite()
        return saving
    }

    @Synchronized
    fun recordListening(item: LibraryItem): Boolean {
        if (session == null) return false
        val timestamp = now()
        val previous = visible.history.find { it.item.id == item.id }
        var updated = account.copy(currentStory = item)
        if (previous == null || timestamp > previous.playedAt) {
            updated = updated.queue(LibraryMutation(LibraryKind.HISTORY, item, playedAt = timestamp))
        }
        if (updated != account) {
            updateAccount(updated)
            onLocalWrite()
        }
        return true
    }

    @Synchronized
    fun importBookmarks(accountId: String, stories: List<LibraryItem>, courses: List<LibraryItem>): Int {
        if (session?.accountId != accountId) return 0
        val savedStoryIds = visible.savedStories.mapTo(mutableSetOf(), LibraryItem::id)
        val savedCourseIds = visible.savedCourses.mapTo(mutableSetOf(), LibraryItem::id)
        val mutations = stories.filter { savedStoryIds.add(it.id) }.map { LibraryMutation(LibraryKind.STORY, it) } +
            courses.filter { savedCourseIds.add(it.id) }.map { LibraryMutation(LibraryKind.COURSE, it) }
        if (mutations.isEmpty()) return 0
        updateAccount(mutations.fold(account, AccountLibrary::queue))
        onLocalWrite()
        return mutations.size
    }

    @Synchronized
    private fun isCurrent(attempt: Long) = attempt == epoch

    @Synchronized
    private fun completeSignIn(attempt: Long, bearer: String, result: Result<VerifiedLibrary>) {
        if (attempt != epoch) return
        val verified = result.getOrNull()
        if (verified == null) {
            mode = if (result.exceptionOrNull() is LibraryAuthenticationRequired) {
                LibrarySyncMode.SIGN_IN_REQUIRED
            } else {
                LibrarySyncMode.OFFLINE
            }
            changed()
            return
        }
        session = Session(verified.accountId, bearer)
        updateAccount(persistence.load(verified.accountId).copy(snapshot = verified.contents))
        mode = readyMode()
        changed()
        if (account.pending.isNotEmpty()) syncNow()
    }

    private fun onLocalWrite() {
        if (mode != LibrarySyncMode.OFFLINE) mode = readyMode()
        changed()
        if (mode != LibrarySyncMode.OFFLINE) syncNow()
    }

    private fun drain() {
        val active = session ?: return
        if (busy) return
        val mutation = account.pending.firstOrNull()
        if (mutation == null && !refreshRequested) return
        if (mutation == null) refreshRequested = false
        val attempt = epoch
        busy = true
        execute {
            if (isCurrent(attempt)) {
                if (mutation == null) {
                    val result = runCatching { transport.fetch(active.bearer) }
                    dispatch { completeRefresh(attempt, active.accountId, result) }
                } else {
                    val result = runCatching { transport.write(active.bearer, mutation) }
                    dispatch { completeWrite(attempt, mutation, result) }
                }
            }
        }
    }

    @Synchronized
    private fun completeWrite(attempt: Long, mutation: LibraryMutation, result: Result<Unit>) {
        if (attempt != epoch) return
        busy = false
        val failure = result.exceptionOrNull()
        if (failure != null && failure !is LibraryMutationRejected) {
            handleFailure(failure)
            return
        }
        if (failure is LibraryMutationRejected) rejectedCount++
        val snapshot = if (failure == null) mutation.applyTo(account.snapshot) else account.snapshot
        // An old acknowledgement must leave the newer local operation for the same item queued.
        updateAccount(account.copy(snapshot = snapshot, pending = account.pending.filterNot { it.revision == mutation.revision }))
        mode = readyMode()
        changed()
        drain()
    }

    @Synchronized
    private fun completeRefresh(attempt: Long, accountId: String, result: Result<VerifiedLibrary>) {
        if (attempt != epoch) return
        busy = false
        val verified = result.getOrNull()
        if (verified == null) {
            handleFailure(checkNotNull(result.exceptionOrNull()))
            return
        }
        if (verified.accountId != accountId) {
            handleFailure(LibraryAuthenticationRequired())
            return
        }
        updateAccount(account.copy(snapshot = verified.contents))
        consecutiveFailures = 0
        mode = readyMode()
        changed()
        drain()
    }

    private fun handleFailure(failure: Throwable) {
        if (failure is LibraryAuthenticationRequired) {
            reset(LibrarySyncMode.SIGN_IN_REQUIRED)
            changed()
            return
        }
        mode = LibrarySyncMode.OFFLINE
        refreshRequested = true
        val ticket = ++retryTicket
        val delay = minOf(60_000L, 5_000L shl consecutiveFailures.coerceAtMost(4))
        consecutiveFailures++
        changed()
        if (consecutiveFailures <= 5) schedule(delay) { retry(ticket) }
    }

    @Synchronized
    private fun retry(ticket: Long) {
        if (ticket == retryTicket) drain()
    }

    private fun updateAccount(updated: AccountLibrary) {
        if (updated == account) return
        account = updated
        visible = updated.visible()
        session?.let { persistence.save(it.accountId, updated) }
    }

    private fun readyMode() = if (account.pending.isEmpty()) LibrarySyncMode.SYNCED else LibrarySyncMode.PENDING

    private fun reset(newMode: LibrarySyncMode) {
        epoch++
        retryTicket++
        session = null
        account = AccountLibrary()
        visible = LibraryContents()
        busy = false
        refreshRequested = false
        consecutiveFailures = 0
        rejectedCount = 0
        mode = newMode
    }
}
