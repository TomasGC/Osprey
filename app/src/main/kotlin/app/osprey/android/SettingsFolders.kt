package app.osprey.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import app.osprey.settings.FolderLocation
import app.osprey.settings.FolderSetting

/**
 * The [FolderLocation] of a SAF tree document id: `primary:Pictures/WhatsApp` is the `primary`
 * volume (the internal storage) and the path below it; an SD card has its own volume id.
 */
fun folderLocationOf(documentId: String): FolderLocation {
    val volume = documentId.substringBefore(':')
    val path = if (':' in documentId) documentId.substringAfter(':') else ""
    return FolderLocation.of(volume, path)
}

/** The [FolderSetting] of a picked tree [uri]: the Uri string as the reference, its name for display. */
fun folderSetting(context: Context, uri: Uri): FolderSetting {
    val documentId = DocumentsContract.getTreeDocumentId(uri)
    val name = DocumentFile.fromTreeUri(context, uri)?.name ?: documentId.substringAfterLast('/')
    return FolderSetting(uri.toString(), folderLocationOf(documentId), name)
}

/** True while the app still holds the persisted read and write grant for [uri]. */
fun hasGrant(context: Context, uri: Uri): Boolean =
    context.contentResolver.persistedUriPermissions
        .any { it.uri == uri && it.isReadPermission && it.isWritePermission }
