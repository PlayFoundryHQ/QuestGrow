package hq.playfoundry.questgrow

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase L — the Persian/RTL client. MockWebServer-backed; drives the real
 * MainActivity/AppRoot. Covers the kid-first shell, pairing, the kid board +
 * completion (INV-8, server-authoritative), and the parent PIN gate + inbox.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val server = MockWebServer()
    private lateinit var app: QuestGrowApp
    private fun today() = java.time.LocalDate.now().toString()

    private fun todayBoard(complete: String? = null) = """
        {"child_id":"kid","on_date":"${today()}",
         "items":[{"quest_id":"teeth","title":"مسواک زدن","icon":"🪥",
                   "state":"${complete ?: "available"}","waits_for_grownup":true}],
         "lifetime_achievement":20,"spendable_balance":10,
         "complexity_profile":{"band":"5-6","text_style":"short_label","audio_narration":"on_tap",
          "iconography":"standard","quests_shown_at_once":5,"interaction":"tap_drag",
          "task_complexity":"small_multi_step","reading_requirement":"minimal","reward_presentation":"animation_progress"}}"""

    private fun dispatcher(completeState: String = "pending", boardState: String? = null) = object : Dispatcher() {
        var approved = false
        var granted = false
        override fun dispatch(req: RecordedRequest): MockResponse {
            val p = req.path.orEmpty()
            fun ok(b: String) = MockResponse().setResponseCode(200).setBody(b)
            return when {
                p.startsWith("/v1/me/today") -> ok(todayBoard(boardState))
                p.startsWith("/v1/me/quests/") && p.endsWith("/complete") -> ok("""{"quest_id":"teeth","state":"$completeState"}""")
                p.startsWith("/v1/me/celebrations") -> ok("""[{"quest_id":"teeth","on_date":"${today()}","points_awarded":10,"at":"2026-08-30T09:00:00+00:00"}]""")
                p == "/v1/me/rewards" && req.method == "GET" -> ok(
                    """{"child_id":"kid","spendable_balance":10,"rewards":[{"reward_id":"ice","name":"بستنی","icon":"🍦","cost":5,"mode":"parent_confirmed","affordable":true,"pending":${granted}}]}""",
                )
                p.startsWith("/v1/me/rewards/") && p.endsWith("/redeem") -> { granted = true; ok("""{"id":"rr1","reward_id":"ice","state":"pending"}""") }
                p == "/v1/redemptions" && req.method == "GET" -> ok(
                    if (granted) "[]"
                    else """[{"id":"rr1","child_id":"kid","child_name":"سارا","reward_id":"ice","reward_name":"بستنی","reward_icon":"🍦","cost":5,"requested_at":"2026-08-30T09:00:00+00:00"}]""",
                )
                p.endsWith("/grant") -> { granted = true; ok("""{"id":"rr1","reward_id":"ice","state":"granted"}""") }
                p.startsWith("/v1/me/progress") -> ok("""{"child_id":"kid","lifetime_achievement":30,"spendable_balance":20,"week_active_days":3}""")
                p == "/v1/auth/login" -> ok("""{"session_token":"s1"}""")
                p == "/v1/auth/unlock" ->
                    if (req.body.readUtf8().contains("\"0000\"")) MockResponse().setResponseCode(403).setBody("""{"detail":"incorrect PIN","code":"not_authorized"}""")
                    else ok("""{"parent_token":"p1"}""")
                p == "/v1/auth/pair" -> ok("""{"child_token":"c_paired"}""")
                p == "/v1/children" && req.method == "GET" ->
                    ok("""[{"child_id":"kid","name":"سارا","age_band":"5-6","avatar":"","birthdate":null}]""")
                p.endsWith("/dashboard") || p.contains("/dashboard?") ->
                    ok("""{"child_id":"kid","on_date":"${today()}","total":2,"verified":1,"pending":1,"available":0,"expired":0,"week_active_days":2}""")
                p == "/v1/quests" && req.method == "GET" -> ok("""[{"quest_id":"teeth","version":1,"title":"مسواک زدن","icon":"🪥","points":10,"active":true,"archived":false}]""")
                p.endsWith("/approve") -> { approved = true; ok("{}") }
                p.endsWith("/approvals") -> ok(if (approved) "[]" else """[{"quest_id":"teeth","on_date":"${today()}","state":"pending"}]""")
                else -> ok("[]")
            }
        }
    }

    @Before fun setUp() {
        server.start()
        app = ApplicationProvider.getApplicationContext()
        val ctx: Context = app
        ctx.getSharedPreferences("questgrow_prefs", Context.MODE_PRIVATE).edit()
            .putString("base_url", server.url("/").toString()).commit()
        File(ctx.filesDir, "questgrow").deleteRecursively()
        kotlinx.coroutines.runBlocking { app.container.tokenStore.clearAll() }
        server.dispatcher = dispatcher()
        app.rebuildContainer()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
        runCatching { kotlinx.coroutines.runBlocking { app.container.tokenStore.clearAll() } }
    }

    /** put the device in the "provisioned as a kid" state and relaunch. */
    private fun provisionKid() {
        kotlinx.coroutines.runBlocking {
            app.container.tokenStore.setChildToken("c_test")
            app.container.tokenStore.setDefaultChild("kid", "سارا")
        }
        app.rebuildContainer()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    /** put the device in the "onboarded parent + kid token" state. */
    private fun provisionParent() {
        kotlinx.coroutines.runBlocking {
            app.container.tokenStore.setAccount("p@x.com", "pw123456")
            app.container.tokenStore.setChildToken("c_test")
            app.container.tokenStore.setDefaultChild("kid", "سارا")
        }
        app.rebuildContainer()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    private fun await(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitDesc(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    // ---------------------------------------------------------------- #

    @Test fun freshInstall_showsWhoseDevice() {
        compose.waitForIdle()
        compose.onNodeWithText("این دستگاه برای کیست؟").assertIsDisplayed()
    }

    @Test fun childDevice_pairs_toKidBoard() {
        compose.waitForIdle()
        compose.onNodeWithText("دستگاه کودک").performClick()
        listOf("۱", "۲", "۳", "۴", "۵", "۶").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("شروع").performClick()
        awaitDesc("مسواک زدن")
        compose.onNodeWithContentDescription("مسواک زدن").assertIsDisplayed()
    }

    @Test fun kidBoard_completion_pending_showsWaiting() {
        provisionKid()
        awaitDesc("مسواک زدن")
        compose.onNodeWithContentDescription("مسواک زدن").performClick()
        compose.onNodeWithText("انجام دادم!").performClick()
        await("منتظر بزرگترت")
        compose.onNodeWithText("منتظر بزرگترت", substring = true).assertIsDisplayed()
    }

    @Test fun kidBoard_completion_verified_showsCelebration() {
        server.dispatcher = dispatcher(completeState = "verified")
        provisionKid()
        awaitDesc("مسواک زدن")
        compose.onNodeWithContentDescription("مسواک زدن").performClick()
        compose.onNodeWithText("انجام دادم!").performClick()
        await("+۱۰")
        compose.onNodeWithText("+۱۰").assertIsDisplayed()
    }

    @Test fun kidBoard_neverSpeaksAStageOrLevel_INV8() {
        provisionKid()
        awaitDesc("مسواک زدن")
        listOf("stage", "level", "PARENT_GUIDED", "CHILD_OWNED", "readiness", "مرحله", "سطح").forEach { w ->
            assertEquals(
                "child surface must not show '$w'", 0,
                compose.onAllNodesWithText(w, substring = true, ignoreCase = true).fetchSemanticsNodes().size +
                    compose.onAllNodesWithContentDescription(w, substring = true, ignoreCase = true).fetchSemanticsNodes().size,
            )
        }
    }

    @Test fun parentGate_wrongPin_thenCorrect_opensHome() {
        provisionParent()
        awaitDesc("بزرگترها")
        compose.onNodeWithContentDescription("بزرگترها").performClick()
        await("رمز والد را وارد کنید")
        listOf("۰", "۰", "۰", "۰").forEach { compose.onNodeWithText(it).performClick() }
        await("رمز اشتباه است")
        listOf("۲", "۴", "۶", "۸").forEach { compose.onNodeWithText(it).performClick() }
        await("تأییدها")
        compose.onNodeWithText("تأییدها").assertIsDisplayed()
    }

    @Test fun kidBoard_rewards_redeem_asksGrownup() {
        provisionKid()
        awaitDesc("مسواک زدن")
        compose.onNodeWithText("جایزه‌ها", substring = true).performClick()
        await("بستنی")
        compose.onNodeWithText("می‌خواهمش").performClick()
        await("این را می‌خواهی؟")
        compose.onNodeWithText("بله").performClick()
        await("به بزرگترت گفتیم")
    }

    @Test fun parentHome_redemptionInbox_grant() {
        provisionParent()
        awaitDesc("بزرگترها")
        compose.onNodeWithContentDescription("بزرگترها").performClick()
        await("رمز والد را وارد کنید")
        listOf("۲", "۴", "۶", "۸").forEach { compose.onNodeWithText(it).performClick() }
        await("درخواست جایزه")
        compose.onNodeWithText("بله، بده").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("درخواست جایزه", substring = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun parentHome_approvalsInbox_approve() {
        provisionParent()
        awaitDesc("بزرگترها")
        compose.onNodeWithContentDescription("بزرگترها").performClick()
        await("رمز والد را وارد کنید")
        listOf("۲", "۴", "۶", "۸").forEach { compose.onNodeWithText(it).performClick() }
        await("هنوز نه")           // an approval card is on screen
        compose.onAllNodesWithText("تأیید")[0].performClick()
        await("همه‌چیز به‌روز است")   // inbox empties
    }
}
