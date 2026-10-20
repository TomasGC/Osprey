package app.osprey.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** First-run prompt, same as the POC's `#noLibrary` panel. */
@Composable
fun NoLibraryScreen(onChooseLibrary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No photo library selected yet.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onChooseLibrary) {
                Text("Choose your photo library folder")
            }
        }
    }
}
