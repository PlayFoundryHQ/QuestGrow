package hq.playfoundry.questgrow

import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.ChildRepository
import hq.playfoundry.questgrow.data.local.FileOfflineQueue
import hq.playfoundry.questgrow.data.local.PendingCompletion
import hq.playfoundry.questgrow.data.model.CompletionOutcome
import hq.playfoundry.questgrow.data.model.QuestVisualState
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

class OfflineQueueTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `enqueue dedupes per quest-day, remove clears, survives reload`() {
        val f = File(tmp.newFolder(), "q.json")
        val q = FileOfflineQueue(f)
        q.enqueue(PendingCompletion("teeth", "2026-08-03"))
        q.enqueue(PendingCompletion("teeth", "2026-08-03"))     // dup — no-op
        q.enqueue(PendingCompletion("teeth", "2026-08-04"))
        assertEquals(2, q.size())
        assertTrue(q.contains("teeth", "2026-08-03"))

        // a fresh instance on the same file sees the same queue (durable)
        assertEquals(2, FileOfflineQueue(f).size())

        q.remove(PendingCompletion("teeth", "2026-08-03"))
        assertFalse(FileOfflineQueue(f).contains("teeth", "2026-08-03"))
        assertEquals(1, FileOfflineQueue(f).size())
    }
}

class ChildRepositorySyncTest {
    private lateinit var server: MockWebServer
    private lateinit var api: QuestGrowApi
    private lateinit var queueFile: File
    private lateinit var repo: ChildRepository

    @get:Rule val tmp = TemporaryFolder()

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        api = ApiClientFactory.create(server.url("/").toString(), TokenProvider { "child-tok" })
        queueFile = File(tmp.newFolder(), "q.json")
        repo = ChildRepository(api, FileOfflineQueue(queueFile)) { 111L }
    }

    @After fun tearDown() = runCatching { server.shutdown() }.let {}

    @Test fun `offline completion is queued, not failed`() = runTest {
        server.shutdown() // no server
        val outcome = repo.complete("teeth", "2026-08-03")
        assertEquals(CompletionOutcome.QueuedOffline, outcome)
        assertEquals(1, repo.pendingCount())
    }

    @Test fun `flush drains the queue on reconnect and drops a 409 (INV-11)`() = runTest {
        FileOfflineQueue(queueFile).apply {
            enqueue(PendingCompletion("teeth", "2026-08-03"))
            enqueue(PendingCompletion("read", "2026-08-03"))
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"quest_id":"teeth","state":"pending"}"""))
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"detail":"already","code":"contract_violation"}"""),
        )
        val cleared = repo.flushQueue()
        assertEquals(2, cleared)                    // one accepted, one already-resolved
        assertEquals(0, repo.pendingCount())
    }

    @Test fun `flush stops and keeps items when it goes offline mid-drain`() = runTest {
        FileOfflineQueue(queueFile).apply {
            enqueue(PendingCompletion("a", "d"))
            enqueue(PendingCompletion("b", "d"))
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"quest_id":"a","state":"pending"}"""))
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        val cleared = repo.flushQueue()
        assertEquals(1, cleared)
        assertEquals(1, repo.pendingCount())         // 'b' stays for the next reconnect
    }

    @Test fun `today marks a still-queued quest as QUEUED_OFFLINE, not available`() = runTest {
        FileOfflineQueue(queueFile).enqueue(PendingCompletion("teeth", "2026-08-03"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"child_id":"mia","on_date":"2026-08-03",
                    "items":[{"quest_id":"teeth","title":"Brush","icon":"x","state":"available","waits_for_grownup":true}],
                    "lifetime_achievement":0,"spendable_balance":0,
                    "complexity_profile":{"band":"5-6","text_style":"short_label","audio_narration":"on_tap",
                     "iconography":"standard","quests_shown_at_once":5,"interaction":"tap_drag",
                     "task_complexity":"small_multi_step","reading_requirement":"minimal","reward_presentation":"animation_progress"}}""",
            ),
        )
        val r = repo.today("2026-08-03")
        assertTrue(r is ApiResult.Ok)
        assertEquals(QuestVisualState.QUEUED_OFFLINE, (r as ApiResult.Ok).value.quests.first().state)
    }

    @Test fun `a verified completion drops any queued copy and reports Verified`() = runTest {
        FileOfflineQueue(queueFile).enqueue(PendingCompletion("teeth", "2026-08-03"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"quest_id":"teeth","state":"verified"}"""))
        assertEquals(CompletionOutcome.Verified, repo.complete("teeth", "2026-08-03"))
        assertEquals(0, repo.pendingCount())
    }
}
