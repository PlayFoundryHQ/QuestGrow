package hq.playfoundry.questgrow

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import hq.playfoundry.questgrow.ui.QuestGrowTheme
import hq.playfoundry.questgrow.ui.child.ChildFlow
import hq.playfoundry.questgrow.ui.onboarding.OnboardingFlow
import hq.playfoundry.questgrow.ui.parent.ParentFlow
import hq.playfoundry.questgrow.ui.parent.ParentGate
import hq.playfoundry.questgrow.ui.persianRtl

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) = super.attachBaseContext(base.persianRtl())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as QuestGrowApp).container
        setContent {
            QuestGrowTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                            AppRoot(container)
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen { Onboarding, Kid, Gate, Parent }

/**
 * Top-level state machine ([[client-redesign]]):
 *  * not onboarded → the guided setup wizard
 *  * onboarded → the **kid board** by default (no login on the family device)
 *  * "بزرگترها" → PIN gate → the parent area; back → kid board
 */
@Composable
private fun AppRoot(container: AppContainer) {
    val onboarded = remember { container.authRepo.isOnboarded() }
    var screen by remember { mutableStateOf(if (onboarded) Screen.Kid else Screen.Onboarding) }

    when (screen) {
        Screen.Onboarding -> OnboardingFlow(container, onDone = { screen = Screen.Kid })
        Screen.Kid -> {
            container.useChildScope()
            ChildFlow(container, onGrownUps = { screen = Screen.Gate })
        }
        Screen.Gate -> ParentGate(
            container,
            onUnlocked = { container.useParentScope(); screen = Screen.Parent },
            onCancel = { screen = Screen.Kid },
        )
        Screen.Parent -> {
            container.useParentScope()
            ParentFlow(container, onExit = { container.useChildScope(); screen = Screen.Kid })
        }
    }
}
