package hq.playfoundry.questgrow

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase J — MockWebServer-backed instrumented UI suite (grant §5). Drives the
 * real [MainActivity] against a scripted `/v1` backend; no live server.
 * Asserts observable product behaviour and accessibility semantics:
 *   * server-authoritative completion (INV-10/11) — the client shows whatever
 *     state the server returns, never invents one;
 *   * INV-8 — the child surface never speaks a stage / level;
 *   * offline capture + a `409` treated as already-resolved;
 *   * parent PIN gate flow and error handling.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val server = MockWebServer()
    private lateinit var app: QuestGrowApp
    private val paths = mutableListOf<String>()

    private fun today() = java.time.LocalDate.now().toString()

    private fun body(name: String): String = when (name) {
        "today_guided" -> """
            {"child_id":"kid","on_date":"${today()}",
             "items":[{"quest_id":"teeth","title":"Brush teeth","icon":"T","state":"available","waits_for_grownup":true}],
             "lifetime_achievement":20,"spendable_balance":10,
             "complexity_profile":{"band":"5-6","text_style":"short_label","audio_narration":"on_tap",
              "iconography":"standard","quests_shown_at_once":5,"interaction":"tap_drag",
              "task_complexity":"small_multi_step","reading_requirement":"minimal","reward_presentation":"animation_progress"}}"""
        "today_owned" -> body("today_guided").replace("\"waits_for_grownup\":true", "\"waits_for_grownup\":false")
        "celebrations" -> """[{"quest_id":"teeth","on_date":"${today()}","points_awarded":10,"at":"2026-08-30T09:00:00+00:00"}]"""
        else -> "{}"
    }

    /** default dispatcher: enough of `/v1` for the child + parent happy paths. */
    private fun dispatcher(childToday: String = "today_guided", completeState: String = "pending") =
        object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val p = req.path.orEmpty(); paths += p
                fun ok(b: String) = MockResponse().setResponseCode(200).setBody(b)
                return when {
                    p.startsWith("/v1/me/today") -> ok(body(childToday))
                    p.startsWith("/v1/me/quests/") && p.endsWith("/complete") ->
                        ok("""{"quest_id":"teeth","state":"$completeState"}""")
                    p.startsWith("/v1/me/celebrations") -> ok(body("celebrations"))
                    p.startsWith("/v1/me/progress") ->
                        ok("""{"child_id":"kid","lifetime_achievement":30,"spendable_balance":20,"week_active_days":3}""")
                    p == "/v1/auth/login" -> ok("""{"session_token":"s1"}""")
                    p == "/v1/auth/unlock" -> ok("""{"parent_token":"p1"}""")
                    p == "/v1/children" && req.method == "GET" ->
                        ok("""[{"child_id":"kid","name":"Kid","age_band":"5-6","avatar":"","birthdate":null}]""")
                    p.endsWith("/dashboard") || p.contains("/dashboard?") ->
                        ok("""{"child_id":"kid","on_date":"${today()}","total":2,"verified":1,"pending":1,"available":0,"expired":0,"week_active_days":2}""")
                    p.endsWith("/approvals") ->
                        ok("""[{"quest_id":"teeth","on_date":"${today()}","state":"pending"}]""")
                    else -> ok("[]")
                }
            }
        }

    @Before fun setUp() {
        server.start()
        app = ApplicationProvider.getApplicationContext()
        val ctx: Context = app
        // fresh state + retarget the backend
        ctx.getSharedPreferences("questgrow_prefs", Context.MODE_PRIVATE).edit()
            .putString("base_url", server.url("/").toString()).commit()
        File(ctx.filesDir, "questgrow").deleteRecursively()
        // clear session through the live DataStore (deleting its file under a
        // running process-singleton store would not drop the in-memory value)
        kotlinx.coroutines.runBlocking { app.container.tokenStore.clearAll() }
        app.rebuildContainer()
        server.dispatcher = dispatcher()
        // relaunch the composition against the new container
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
        runCatching { kotlinx.coroutines.runBlocking { app.container.tokenStore.clearAll() } }
    }

    // --------------------------------------------------------------------- #
    @Test fun childCode_to_today_rendersServerBoard() {
        compose.onNodeWithText("I'm a kid").performClick()
        compose.onNodeWithText("Code").performTextInput("c_test")
        compose.onNodeWithText("Start").performClick()
        awaitContentDescription("Brush teeth")
        // the card title comes from the server payload
        compose.onNodeWithContentDescription("Brush teeth").assertIsDisplayed()
        assertTrue(paths.any { it.startsWith("/v1/me/today") })
    }

    @Test fun completion_isServerAuthoritative_pending_showsWaiting() {
        openChildToday()
        compose.onNodeWithContentDescription("Brush teeth").performClick()
        compose.onNodeWithText("I did it!").performClick()
        awaitTag("waiting")
        // server said "pending" → the child sees the calm waiting state, not a celebration
        compose.onNodeWithTag("waiting", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun completion_isServerAuthoritative_verified_showsCelebration() {
        server.dispatcher = dispatcher(completeState = "verified")
        openChildToday()
        compose.onNodeWithContentDescription("Brush teeth").performClick()
        compose.onNodeWithText("I did it!").performClick()
        awaitTag("celebration")
        compose.onNodeWithTag("celebration", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("+10").assertIsDisplayed()   // points from /v1/me/celebrations, not local
    }

    @Test fun childSurface_neverSpeaksAStageOrLevel_INV8() {
        openChildToday()
        // walk every content-desc on the child board
        listOf("Brush teeth").forEach { compose.onNodeWithContentDescription(it).assertExists() }
        // the forbidden vocabulary must not appear anywhere on screen
        listOf("stage", "level", "PARENT_GUIDED", "CHILD_OWNED", "readiness", "independence").forEach { word ->
            assertEquals("child surface must not show '$word'", 0,
                compose.onAllNodesWithText(word, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().size)
        }
    }

    @Test fun offlineCompletion_isQueued_notFailed() {
        openChildToday()
        server.shutdown()   // now offline
        compose.onNodeWithContentDescription("Brush teeth").performClick()
        compose.onNodeWithText("I did it!").performClick()
        awaitTag("waiting")
        compose.onNodeWithTag("waiting", useUnmergedTree = true).assertIsDisplayed()   // queued → "waiting", not an error
        assertEquals(1, app.container.childRepo.pendingCount())
    }

    @Test fun parent_pinGate_thenDashboard() {
        compose.onNodeWithText("I'm a grown-up").performClick()
        compose.onNodeWithTag("email").performTextInput("p@x.com")
        compose.onNodeWithTag("password").performTextInput("pw123456")
        compose.onNodeWithTag("pin").performTextInput("2468")
        compose.onNodeWithText("Unlock parent mode").performClick()
        awaitTag("tab_Dashboard")
        compose.onNodeWithTag("tab_Dashboard", useUnmergedTree = true).assertIsDisplayed()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Kid").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Kid").assertExists()   // the child row from /v1/children
        assertTrue(paths.contains("/v1/auth/login") && paths.contains("/v1/auth/unlock"))
    }

    @Test fun parent_signIn_wrongPin_showsError() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                paths += req.path.orEmpty()
                return if (req.path == "/v1/auth/login")
                    MockResponse().setResponseCode(200).setBody("""{"session_token":"s1"}""")
                else MockResponse().setResponseCode(403)
                    .setBody("""{"detail":"incorrect PIN","code":"not_authorized"}""")
            }
        }
        compose.onNodeWithText("I'm a grown-up").performClick()
        compose.onNodeWithTag("email").performTextInput("p@x.com")
        compose.onNodeWithTag("password").performTextInput("pw123456")
        compose.onNodeWithTag("pin").performTextInput("0000")
        compose.onNodeWithText("Unlock parent mode").performClick()
        awaitTag("signin_message")
        compose.onNodeWithTag("signin_message", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun parent_approvals_emptyState_whenNothingWaiting() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val p = req.path.orEmpty()
                fun ok(b: String) = MockResponse().setResponseCode(200).setBody(b)
                return when {
                    p == "/v1/auth/login" -> ok("""{"session_token":"s1"}""")
                    p == "/v1/auth/unlock" -> ok("""{"parent_token":"p1"}""")
                    p == "/v1/children" -> ok("""[{"child_id":"kid","name":"Kid","age_band":"5-6","avatar":"","birthdate":null}]""")
                    p.endsWith("/approvals") -> ok("[]")
                    else -> ok("[]")
                }
            }
        }
        signInParent()
        compose.onNodeWithTag("tab_Approvals", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Load queue").performClick()
        awaitTag("approvals_empty")
        compose.onNodeWithTag("approvals_empty", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun parent_quests_errorState_showsRetry() {
        server.dispatcher = object : Dispatcher() {
            var questsCalls = 0
            override fun dispatch(req: RecordedRequest): MockResponse {
                val p = req.path.orEmpty()
                fun ok(b: String) = MockResponse().setResponseCode(200).setBody(b)
                return when {
                    p == "/v1/auth/login" -> ok("""{"session_token":"s1"}""")
                    p == "/v1/auth/unlock" -> ok("""{"parent_token":"p1"}""")
                    p == "/v1/children" -> ok("""[{"child_id":"kid","name":"Kid","age_band":"5-6","avatar":"","birthdate":null}]""")
                    p == "/v1/quests" -> {
                        questsCalls++
                        if (questsCalls == 1) MockResponse().setResponseCode(500)
                            .setBody("""{"detail":"boom","code":"error"}""")
                        else ok("""[{"quest_id":"teeth","version":1,"title":"Brush teeth","icon":"T","points":10,"active":true,"archived":false}]""")
                    }
                    else -> ok("[]")
                }
            }
        }
        signInParent()
        compose.onNodeWithTag("tab_Quests", useUnmergedTree = true).performClick()
        awaitTag("quests_error")
        compose.onNodeWithTag("quests_error", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        awaitTag("quests_list")
        compose.onNodeWithTag("quests_list", useUnmergedTree = true).assertExists()
    }

    @Test fun cachedToday_whenOffline_showsLastBoardMarkedStale() {
        openChildToday()                         // online fetch → board is cached
        server.shutdown()                        // now offline
        compose.activityRule.scenario.recreate() // relaunch: child token persists → child Today reloads
        // the last-known board is still shown …
        awaitContentDescription("Brush teeth")
        compose.onNodeWithContentDescription("Brush teeth").assertExists()
        // … explicitly marked as offline / stale, never as a fresh board
        compose.onNodeWithContentDescription("Offline. Showing your last board.").assertExists()
    }

    @Test fun parentTabs_navigateBetweenSections() {
        signInParent()
        // a distinctive node on each parent sub-tab, proving navigation works
        mapOf(
            "tab_Approvals" to "Load queue",
            "tab_Family" to "Age-adaptation overrides",
            "tab_Quests" to "One-tap starter templates",
            "tab_Rewards" to "New reward",
            "tab_Ownership" to "Set support level for a quest",
            "tab_Settings" to "Backend server",
        ).forEach { (tab, marker) ->
            compose.onNodeWithTag(tab, useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        // and back to the first tab
        compose.onNodeWithTag("tab_Dashboard", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Add a child", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // --------------------------------------------------------------------- #
    /** network responses aren't Espresso idling resources, so poll the tree. */
    private fun awaitContentDescription(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openChildToday() {
        compose.onNodeWithText("I'm a kid").performClick()
        compose.onNodeWithText("Code").performTextInput("c_test")
        compose.onNodeWithText("Start").performClick()
        awaitContentDescription("Brush teeth")
    }

    private fun signInParent() {
        compose.onNodeWithText("I'm a grown-up").performClick()
        compose.onNodeWithTag("email").performTextInput("p@x.com")
        compose.onNodeWithTag("password").performTextInput("pw")
        compose.onNodeWithTag("pin").performTextInput("2468")
        compose.onNodeWithText("Unlock parent mode").performClick()
        awaitTag("tab_Dashboard")
    }
}
