package hq.playfoundry.questgrow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.QuestGrowTheme
import hq.playfoundry.questgrow.ui.child.ChildFlow
import hq.playfoundry.questgrow.ui.parent.ParentFlow

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as QuestGrowApp).container

        setContent {
            QuestGrowTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var mode by remember {
                        mutableStateOf(
                            if (container.tokenStore.childTokenBlocking() != null) Mode.CHILD else Mode.CHOOSER,
                        )
                    }
                    when (mode) {
                        Mode.CHOOSER -> ModeChooser(
                            onChild = { container.useChildScope(); mode = Mode.CHILD },
                            onParent = { container.useParentScope(); mode = Mode.PARENT },
                        )
                        Mode.CHILD -> {
                            container.useChildScope()
                            ChildFlow(container, onExit = { mode = Mode.CHOOSER })
                        }
                        Mode.PARENT -> {
                            container.useParentScope()
                            ParentFlow(container, onExit = { mode = Mode.CHOOSER })
                        }
                    }
                }
            }
        }
    }

    private enum class Mode { CHOOSER, CHILD, PARENT }

    @Composable
    private fun ModeChooser(onChild: () -> Unit, onParent: () -> Unit) {
        Column(
            Modifier.fillMaxSize().padding(28.dp).testTag("mode_chooser"),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text("QuestGrow", style = MaterialTheme.typography.headlineMedium)
            BigButton("I'm a kid", contentDescription = "Open the kid screen") { onChild() }
            BigButton("I'm a grown-up", contentDescription = "Open the grown-up screen") { onParent() }
        }
    }
}
