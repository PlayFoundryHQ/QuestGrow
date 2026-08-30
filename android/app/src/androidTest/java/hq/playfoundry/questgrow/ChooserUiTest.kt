package hq.playfoundry.questgrow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.QuestGrowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A minimal instrumented Compose test — proves the UI harness runs on a
 * device and that the shared [BigButton] carries its accessibility label and
 * fires its click. The full child/parent flows are exercised end-to-end
 * against the live backend in Phase G's emulator drive (see E_READINESS-style
 * notes in the Phase G report).
 */
class ChooserUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bigButton_has_semantics_and_click() {
        var clicked = false
        rule.setContent {
            QuestGrowTheme {
                BigButton(text = "I did it!", contentDescription = "I did Brush teeth") { clicked = true }
            }
        }
        rule.onNodeWithContentDescription("I did Brush teeth").assertIsDisplayed()
        rule.onNodeWithText("I did it!").performClick()
        assertTrue(clicked)
    }
}
