package app.osprey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import app.osprey.android.ui.OspreyApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = (application as OspreyApplication).appState
        setContent {
            LaunchedEffect(Unit) { state.start() }
            OspreyApp(state)
        }
    }
}
