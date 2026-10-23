package app.osprey.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Launches the system SAF folder picker (the only way to grant a tree permission -- no API can
 * do it non-interactively, confirmed by #9's spike) and persists the resulting grant so it
 * survives process death and app restarts. Returns a zero-arg trigger, so call sites don't need
 * to know anything about Activity Result contracts. What the folder is for is the caller's business:
 * [onPicked] decides, and hands the grant back through [releaseGrant] if it does not keep the folder.
 */
@Composable
fun rememberFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            onPicked(uri)
        }
    }
    return { launcher.launch(null) }
}

/** Gives back the persisted grant of the folder behind [reference] (a tree Uri string). */
fun releaseGrant(context: Context, reference: String) {
    context.contentResolver.releasePersistableUriPermission(
        Uri.parse(reference),
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
}
