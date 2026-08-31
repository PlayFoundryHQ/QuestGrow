package hq.playfoundry.questgrow

import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.ChildRepository
import hq.playfoundry.questgrow.data.local.FileOfflineQueue
import hq.playfoundry.questgrow.data.local.ReadCache
import hq.playfoundry.questgrow.data.net.ApiClientFactory
import hq.playfoundry.questgrow.data.net.QuestGrowApi
import hq.playfoundry.questgrow.data.net.TokenProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Grant §1 — "offline behavior and cached reads where appropriate". A cached
 * board is served when the device is offline, always flagged `stale`; the
 * server stays authoritative (a fresh success overwrites the cache and clears
 * the flag).
 */
class OfflineCacheTest {
    private lateinit var server: MockWebServer
    private lateinit var api: QuestGrowApi
    private lateinit var cache: ReadCache
    private lateinit var repo: ChildRepository

    @get:Rule val tmp = TemporaryFolder()

    private val todayBody = """
        {"child_id":"mia","on_date":"2026-08-03",
         "items":[{"quest_id":"teeth","title":"Brush","icon":"x","state":"available","waits_for_grownup":true}],
         "lifetime_achievement":40,"spendable_balance":10,
         "complexity_profile":{"band":"5-6","text_style":"short_label","audio_narration":"on_tap",
          "iconography":"standard","quests_shown_at_once":5,"interaction":"tap_drag",
          "task_complexity":"small_multi_step","reading_requirement":"minimal","reward_presentation":"animation_progress"}}
    """.trimIndent()

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        api = ApiClientFactory.create(server.url("/").toString(), TokenProvider { "c" })
        cache = ReadCache(tmp.newFolder())
        repo = ChildRepository(api, FileOfflineQueue(File(tmp.newFolder(), "q.json")), cache)
    }

    @After fun tearDown() = runCatching { server.shutdown() }.let {}

    @Test fun `offline with no cache is still Offline`() = runTest {
        server.shutdown()
        assertTrue(repo.today("2026-08-03") is ApiResult.Offline)
    }

    @Test fun `a live board is cached, then served stale when offline`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(todayBody))
        val live = repo.today("2026-08-03")
        assertTrue(live is ApiResult.Ok)
        assertFalse((live as ApiResult.Ok).value.stale)
        assertEquals(40, live.value.lifetimeAchievement)

        server.shutdown()
        val offline = repo.today("2026-08-03")
        assertTrue(offline is ApiResult.Ok)
        assertTrue((offline as ApiResult.Ok).value.stale)        // marked stale
        assertEquals("Brush", offline.value.quests.first().title) // last-known content
    }

    @Test fun `a fresh success overwrites the cache and clears stale`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(todayBody))
        repo.today("2026-08-03")
        val newer = todayBody.replace("\"lifetime_achievement\":40", "\"lifetime_achievement\":55")
        server.enqueue(MockResponse().setResponseCode(200).setBody(newer))
        val r = repo.today("2026-08-03") as ApiResult.Ok
        assertFalse(r.value.stale)
        assertEquals(55, r.value.lifetimeAchievement)
        assertEquals(55, cache.getToday(null)!!.lifetimeAchievement)
    }

    @Test fun `progress is cached and served stale offline`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"child_id":"mia","lifetime_achievement":40,"spendable_balance":10,"week_active_days":4}"""))
        assertFalse((repo.progress("2026-08-03") as ApiResult.Ok).value.stale)
        server.shutdown()
        val p = repo.progress("2026-08-03") as ApiResult.Ok
        assertTrue(p.value.stale)
        assertEquals(4, p.value.weekActiveDays)
    }

    @Test fun `a 4xx is a Failure, never the cache`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(todayBody))
        repo.today("2026-08-03")
        server.enqueue(MockResponse().setResponseCode(401)
            .setBody("""{"detail":"expired","code":"not_authenticated"}"""))
        assertTrue(repo.today("2026-08-03") is ApiResult.Failure)   // not the stale cache
    }
}
