package app.osprey.android.filesystem

import androidx.documentfile.provider.DocumentFile
import app.osprey.filesystem.FileHandle

/** Real [FileHandle]: a Storage Access Framework [DocumentFile]. */
data class AndroidFileHandle(val documentFile: DocumentFile) : FileHandle
