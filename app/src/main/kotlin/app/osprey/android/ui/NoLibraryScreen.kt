package app.osprey.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * First-run prompt: SAF has no way to grant a folder without the interactive system picker.
 * Its own [Surface] fills the theme's background -- shown before [androidx.compose.material3.Scaffold]
 * exists to do that for every other screen.
 */
@Composable
fun NoLibraryScreen(onChooseLibrary: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No photo library selected yet.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onChooseLibrary) {
                    Text("Choose your photo library folder")
                }
            }
        }
    }
}
