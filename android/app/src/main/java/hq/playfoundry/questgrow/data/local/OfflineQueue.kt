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
 */
@Serializable
data class PendingCompletion(
    val questId: String,
    val day: String,
    val note: String = "",
    val enqueuedAt: Long = 0L,
)

interface OfflineQueue {
    fun all(): List<PendingCompletion>
    fun enqueue(item: PendingCompletion)
    fun remove(item: PendingCompletion)
    fun contains(questId: String, day: String): Boolean
    fun size(): Int
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

    override fun all(): List<PendingCompletion> = read()

    override fun enqueue(item: PendingCompletion) {
        val items = read()
        // one intent per (quest, day) — a re-tap while still offline is a no-op
        if (items.none { it.questId == item.questId && it.day == item.day }) {
            items.add(item)
            write(items)
        }
    }

    override fun remove(item: PendingCompletion) {
        write(read().filterNot { it.questId == item.questId && it.day == item.day })
    }

    override fun contains(questId: String, day: String): Boolean =
        read().any { it.questId == questId && it.day == day }

    override fun size(): Int = read().size
}
