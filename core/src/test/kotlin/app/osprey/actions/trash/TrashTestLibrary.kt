package app.osprey.actions.trash

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle

/** A library on the fake file system, with helpers to fill its Trash and to look at what is left. */
class TrashTestLibrary {

    val fs = FakeFileSystemRepository()
    val root: FileHandle get() = fs.root

    /** Puts [name] in `Trash/[year]/[action]` (creating the folders) and returns it as the scanner sees it. */
    fun trash(year: String, action: TrashAction, name: String, bytes: ByteArray = name.toByteArray()): TrashEntry {
        val folder = TrashFolders(fs).ensure(TrashLocation(root, year), action)
        fs.addFile(folder, name, bytes)
        return TrashScanner(fs).scan(root).entries(year, action).single { it.name == name }
    }

    /** Puts [name] in the library's year folder (created if needed). */
    fun library(year: String, name: String, bytes: ByteArray = name.toByteArray()): FileHandle =
        fs.addFile(fs.createFolder(root, year), name, bytes)

    fun namesIn(vararg path: String): Set<String> {
        var folder: FileHandle = root
        for (segment in path) {
            folder = fs.list(folder).firstOrNull { it.isDirectory && it.name == segment }?.handle ?: return emptySet()
        }
        return fs.list(folder).map { it.name }.toSet()
    }

    fun exists(vararg path: String): Boolean {
        var folder: FileHandle = root
        for (segment in path) {
            folder = fs.list(folder).firstOrNull { it.name == segment }?.handle ?: return false
        }
        return true
    }
}
