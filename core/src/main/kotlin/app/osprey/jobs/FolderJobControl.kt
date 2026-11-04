package app.osprey.jobs

import app.osprey.history.FileRecord

/**
 * [this] with every file it records placed under [folder] (a year, say): `2021/sub/a.jpg` rather than `sub/a.jpg`, so a
 * run page is enough to find a file (#79). Without a folder it is [this] itself.
 */
fun JobControl.underFolder(folder: String?): JobControl =
    if (folder.isNullOrBlank()) this else UnderFolderControl(this, folder)

private class UnderFolderControl(private val inner: JobControl, private val folder: String) : JobControl by inner {
    override fun record(file: FileRecord) = inner.record(file.copy(path = "$folder/${file.path}"))
}
