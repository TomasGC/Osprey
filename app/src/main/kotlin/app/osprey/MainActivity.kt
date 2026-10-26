package app.osprey

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.osprey.android.ui.OspreyApp
import app.osprey.jobs.JobState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val askForNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var askedForNotifications = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OspreyApplication
        val state = app.appState
        setContent {
            LaunchedEffect(Unit) { state.start() }
            OspreyApp(state)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.jobs.entries.collect { entries ->
                    if (entries.any { it.state !is JobState.Finished }) askForNotificationsOnce()
                }
            }
        }
    }

    /**
     * Android 13 and later show a job's notification only once the user allowed it. The first job asks, once per
     * run of the app; a refusal does not stop the job, it just runs without a notification.
     */
    private fun askForNotificationsOnce() {
        if (askedForNotifications || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        askedForNotifications = true
        askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
