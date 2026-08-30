package hq.playfoundry.questgrow.data.local

import hq.playfoundry.questgrow.data.net.ProgressDto
import hq.playfoundry.questgrow.data.net.TodayDto
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Last-known-good child read models, on disk (grant §1 — "offline behavior and
 * cached reads where appropriate"). A child device holds one child token, so
 * this is single-slot. The server stays authoritative: a cached board is
 * always surfaced as **stale** so the viewer knows it may be out of date, and
 * a completion tapped on a stale board is still queued through the normal
 * offline path.
 *
 * We cache the raw wire DTOs (already `@Serializable`) and re-map on read.
 */
class ReadCache(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val todayFile get() = File(dir, "today.json")
    private val progressFile get() = File(dir, "progress.json")

    fun putToday(dto: TodayDto) = runCatching {
        dir.mkdirs(); todayFile.writeText(json.encodeToString(TodayDto.serializer(), dto))
    }

    fun getToday(): TodayDto? = todayFile.takeIf { it.exists() }?.let {
        runCatching { json.decodeFromString(TodayDto.serializer(), it.readText()) }.getOrNull()
    }

    fun putProgress(dto: ProgressDto) = runCatching {
        dir.mkdirs(); progressFile.writeText(json.encodeToString(ProgressDto.serializer(), dto))
    }

    fun getProgress(): ProgressDto? = progressFile.takeIf { it.exists() }?.let {
        runCatching { json.decodeFromString(ProgressDto.serializer(), it.readText()) }.getOrNull()
    }

    fun clear() = runCatching { dir.listFiles()?.forEach { it.delete() } }
}
