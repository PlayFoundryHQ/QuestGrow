package hq.playfoundry.questgrow.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A durable queue of child "I did it" intents captured while offline
 * (grant §9). Kept deliberately simple — a JSON list in a file — so it is
 * fully unit-testable on the JVM and has no codegen/runtime dependency. The
 * server remains authoritative: entries are enqueued as *intent* only, and a
 * `409` on replay means "already resolved" → drop, not fail (INV-11).
 *
 * Each entry is bound to the [childId] it was tapped for (DECISION-021 — the
 * family device holds several children). A flush replays an entry only with
 * that child's own token; switching the active child never re-attributes a
 * pending completion. An empty [childId] is a legacy entry (single-child
 * paired device, or a queue written before this field existed) and is flushed
 * against whichever child is active.
 */
@Serializable
data class PendingCompletion(
    val questId: String,
    val day: String,
    val note: String = "",
    val enqueuedAt: Long = 0L,
    val childId: String = "",
)

interface OfflineQueue {
    fun all(): List<PendingCompletion>
    fun enqueue(item: PendingCompletion)
    fun remove(item: PendingCompletion)
    fun contains(childId: String, questId: String, day: String): Boolean
    fun size(): Int
    /** Drop every entry belonging to [childId] (e.g. the child left this device). */
    fun removeAllFor(childId: String)
}

class FileOfflineQueue(private val file: File) : OfflineQueue {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(PendingCompletion.serializer())
    private val lock = Any()

    private fun read(): MutableList<PendingCompletion> = synchronized(lock) {
        if (!file.exists()) return mutableListOf()
        return runCatching {
            json.decodeFromString(listSerializer, file.readText()).toMutableList()
        }.getOrDefault(mutableListOf())
    }

    private fun write(items: List<PendingCompletion>) = synchronized(lock) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(listSerializer, items))
    }

    private fun sameIntent(a: PendingCompletion, b: PendingCompletion) =
        a.childId == b.childId && a.questId == b.questId && a.day == b.day

    override fun all(): List<PendingCompletion> = read()

    override fun enqueue(item: PendingCompletion) {
        val items = read()
        // one intent per (child, quest, day) — a re-tap while still offline is a no-op
        if (items.none { sameIntent(it, item) }) {
            items.add(item)
            write(items)
        }
    }

    override fun remove(item: PendingCompletion) {
        write(read().filterNot { sameIntent(it, item) })
    }

    override fun contains(childId: String, questId: String, day: String): Boolean =
        read().any { it.childId == childId && it.questId == questId && it.day == day }

    override fun size(): Int = read().size

    override fun removeAllFor(childId: String) {
        write(read().filterNot { it.childId == childId })
    }
}
