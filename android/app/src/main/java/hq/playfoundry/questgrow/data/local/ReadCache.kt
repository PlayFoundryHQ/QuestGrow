package hq.playfoundry.questgrow.data.local

import hq.playfoundry.questgrow.data.net.ProgressDto
import hq.playfoundry.questgrow.data.net.TodayDto
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Last-known-good child read models, on disk (grant §1 — "offline behavior and
 * cached reads where appropriate"). The server stays authoritative: a cached
 * board is always surfaced as **stale** so the viewer knows it may be out of
 * date, and a completion tapped on a stale board is still queued through the
 * normal offline path.
 *
 * Cached **per child** (DECISION-021 — the family device holds several
 * children): switching the active child while offline shows that child's own
 * last board, never the previous child's. Files are `today-<childId>.json` /
 * `progress-<childId>.json`; a null/blank id (single-child paired device) uses
 * the `_` slot. We cache the raw wire DTOs (already `@Serializable`) and re-map
 * on read.
 */
class ReadCache(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun slot(childId: String?): String =
        childId?.takeIf { it.isNotBlank() }?.replace(Regex("[^A-Za-z0-9_-]"), "_") ?: "_"

    private fun todayFile(childId: String?) = File(dir, "today-${slot(childId)}.json")
    private fun progressFile(childId: String?) = File(dir, "progress-${slot(childId)}.json")

    fun putToday(childId: String?, dto: TodayDto) = runCatching {
        dir.mkdirs()
        todayFile(childId).writeText(json.encodeToString(TodayDto.serializer(), dto))
    }

    fun getToday(childId: String?): TodayDto? = todayFile(childId).takeIf { it.exists() }?.let {
        runCatching { json.decodeFromString(TodayDto.serializer(), it.readText()) }.getOrNull()
    }

    fun putProgress(childId: String?, dto: ProgressDto) = runCatching {
        dir.mkdirs()
        progressFile(childId).writeText(json.encodeToString(ProgressDto.serializer(), dto))
    }

    fun getProgress(childId: String?): ProgressDto? = progressFile(childId).takeIf { it.exists() }?.let {
        runCatching { json.decodeFromString(ProgressDto.serializer(), it.readText()) }.getOrNull()
    }

    /** Drop one child's cached board + progress (e.g. the child left this device). */
    fun forgetChild(childId: String?) = runCatching {
        todayFile(childId).delete(); progressFile(childId).delete()
    }

    fun clear() = runCatching { dir.listFiles()?.forEach { it.delete() } }
}
