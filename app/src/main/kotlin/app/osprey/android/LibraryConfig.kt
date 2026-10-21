package app.osprey.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile

private const val PREFS_NAME = "osprey_config"
private const val KEY_LIBRARY_URI = "library_tree_uri"

/**
 * Library-folder persistence, SAF-flavored equivalent of desktop's `OspreyConfig`: a tree Uri is
 * only ever trusted if its persisted read/write grant is still held by the content resolver --
 * the grant can outlive this app's own install across a reinstall (#13's instrumented tests rely
 * on exactly that), but a revoked grant must never be silently reused, so [resolveLibraryRoot]
 * checks [android.content.ContentResolver.getPersistedUriPermissions], not just the saved string.
 */
object AndroidOspreyConfig {

    fun saveLibraryUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIBRARY_URI, uri.toString())
            .apply()
    }

    /** The saved library tree, or null if nothing was ever saved or its grant was since revoked. */
    fun resolveLibraryRoot(context: Context): DocumentFile? {
        val savedUri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIBRARY_URI, null)
            ?.let(Uri::parse)
            ?: return null
        val stillGranted = context.contentResolver.persistedUriPermissions
            .any { it.uri == savedUri && it.isReadPermission && it.isWritePermission }
        if (!stillGranted) return null
        return DocumentFile.fromTreeUri(context, savedUri)?.takeIf { it.isDirectory }
    }
}

/**
 * Launches the system SAF folder picker (the only way to grant a tree permission -- no API can
 * do it non-interactively, confirmed by #9's spike) and persists the resulting grant so it
 * survives process death and app restarts. Returns a zero-arg trigger, so call sites don't need
 * to know anything about Activity Result contracts.
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
            AndroidOspreyConfig.saveLibraryUri(context, uri)
            onPicked(uri)
        }
    }
    return { launcher.launch(null) }
}
