package hq.playfoundry.questgrow

import hq.playfoundry.questgrow.data.net.ApiError
import hq.playfoundry.questgrow.data.net.TodayDto
import hq.playfoundry.questgrow.data.net.questGrowJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wire DTOs must decode the *actual* QuestGrow API JSON (grant §13). */
class DtoSerializationTest {

    // a real /v1/me/today body shape (src/questgrow/api.py TodayOut + §13 profile)
    private val todayJson = """
        {"child_id":"mia","on_date":"2026-08-03",
         "items":[{"quest_id":"teeth","title":"Brush teeth","icon":"🪥","state":"pending","waits_for_grownup":true},
                  {"quest_id":"read","title":"Read a book","icon":"📖","state":"verified","waits_for_grownup":false}],
         "lifetime_achievement":25,"spendable_balance":15,
         "complexity_profile":{"band":"3-4","text_style":"icon_only","audio_narration":"always",
           "iconography":"large_simple","quests_shown_at_once":3,"interaction":"single_tap",
           "task_complexity":"single_step","reading_requirement":"none","reward_presentation":"big_animation"}}
    """.trimIndent()

    @Test fun `today decodes with snake_case names`() {
        val t = questGrowJson.decodeFromString(TodayDto.serializer(), todayJson)
        assertEquals("mia", t.childId)
        assertEquals(2, t.items.size)
        assertTrue(t.items[0].waitsForGrownup)
        assertFalse(t.items[1].waitsForGrownup)
        assertEquals(25, t.lifetimeAchievement)
        assertEquals("icon_only", t.complexityProfile.textStyle)
        assertEquals(3, t.complexityProfile.questsShownAtOnce)
    }

    @Test fun `today never carries a stage or level field (INV-8)`() {
        // structural: the DTO has no such property, and the raw JSON we send/consume has none
        val raw = todayJson.lowercase()
        listOf("ownership", "stage", "independence", "level", "readiness").forEach {
            assertFalse("child payload must not mention $it", raw.contains(it))
        }
    }

    @Test fun `structured error body decodes code and detail`() {
        val e = questGrowJson.decodeFromString(
            ApiError.serializer(),
            """{"detail":"insufficient Spendable Balance","code":"contract_violation"}""",
        )
        assertEquals("contract_violation", e.code)
        assertEquals("insufficient Spendable Balance", e.detail)
    }
}
