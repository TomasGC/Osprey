package app.osprey.desktop

import java.nio.file.Path
import javax.swing.JFileChooser

/**
 * Native folder-picker dialog, replacing the POC's `webview.create_file_dialog(FOLDER_DIALOG)`.
 * `JFileChooser` in directories-only mode is the reliable cross-platform way to ask for a
 * folder specifically -- `java.awt.FileDialog`'s directory-selection support is inconsistent
 * across platforms.
 */
fun pickFolder(title: String = "Choose your photo library folder"): Path? {
    val chooser = JFileChooser()
    chooser.dialogTitle = title
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}
