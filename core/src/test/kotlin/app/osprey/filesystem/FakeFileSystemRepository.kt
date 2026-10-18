package app.osprey.filesystem

/**
 * In-memory [FileSystemRepository], backing every `:core` planner's unit tests from here on.
 * Seeded and inspected through [addFile]/[addFolder]/[root] — those are fake-only test
 * scaffolding, not part of the interface under test.
 */
class FakeFileSystemRepository : FileSystemRepository {

    private class Node(
        var name: String,
        var parentId: Long,
        var isDirectory: Boolean,
        var bytes: ByteArray = ByteArray(0),
        var timestamps: FileTimestamps = FileTimestamps(),
    )

    private data class Handle(val id: Long) : FileHandle

    private val nodes = mutableMapOf<Long, Node>()
    private var nextId = 1L

    /** The repository's single top-level folder; every [addFile]/[addFolder] call starts from here. */
    val root: FileHandle = Handle(0L).also { nodes[0L] = Node(name = "", parentId = -1L, isDirectory = true) }

    /** Test scaffolding: creates [name] as a file directly inside [parent]. */
    fun addFile(parent: FileHandle, name: String, bytes: ByteArray = ByteArray(0)): FileHandle =
        newNode(parent, name, isDirectory = false, bytes = bytes)

    /** Test scaffolding: creates [name] as a subfolder directly inside [parent]. */
    fun addFolder(parent: FileHandle, name: String): FileHandle =
        newNode(parent, name, isDirectory = true)

    override fun list(folder: FileHandle): List<FileEntry> {
        val folderId = folder.id()
        if (nodes[folderId]?.isDirectory != true) return emptyList()
        return nodes.filterValues { it.parentId == folderId }
            .map { (id, node) -> node.toEntry(id) }
            .sortedBy { it.name }
    }

    override fun rename(entry: FileHandle, newName: String): FileHandle {
        val id = entry.id()
        val node = nodes.getValue(id)
        requireNoCollision(node.parentId, newName, exceptId = id)
        node.name = newName
        return entry
    }

    override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        val id = entry.id()
        val node = nodes.getValue(id)
        val destId = destFolder.id()
        val targetName = newName ?: node.name
        requireNoCollision(destId, targetName, exceptId = id)
        node.parentId = destId
        node.name = targetName
        return entry
    }

    override fun createFolder(parent: FileHandle, name: String): FileHandle {
        val parentId = parent.id()
        val existing = nodes.entries.find { it.value.parentId == parentId && it.value.name == name }
        if (existing != null) {
            if (existing.value.isDirectory) return Handle(existing.key)
            throw FileAlreadyExistsException("$name already exists as a file")
        }
        return newNode(parent, name, isDirectory = true)
    }

    override fun readBytes(entry: FileHandle): ByteArray = nodes.getValue(entry.id()).bytes

    override fun readTimestamps(entry: FileHandle): FileTimestamps = nodes.getValue(entry.id()).timestamps

    override fun writeTimestamps(entry: FileHandle, timestamps: FileTimestamps) {
        val node = nodes.getValue(entry.id())
        node.timestamps = FileTimestamps(
            created = timestamps.created ?: node.timestamps.created,
            modified = timestamps.modified ?: node.timestamps.modified,
        )
    }

    private fun newNode(parent: FileHandle, name: String, isDirectory: Boolean, bytes: ByteArray = ByteArray(0)): FileHandle {
        val id = nextId++
        nodes[id] = Node(name, parent.id(), isDirectory, bytes)
        return Handle(id)
    }

    private fun requireNoCollision(parentId: Long, name: String, exceptId: Long) {
        val collision = nodes.entries.any { (id, node) -> id != exceptId && node.parentId == parentId && node.name == name }
        if (collision) throw FileAlreadyExistsException("$name already exists")
    }

    private fun Node.toEntry(id: Long): FileEntry = FileEntry(Handle(id), name, isDirectory, bytes.size.toLong())

    private fun FileHandle.id(): Long = (this as Handle).id
}
