package app.osprey.media

private const val IROT_BOX_SIZE = 9
private const val ESSENTIAL_8 = 0x80
private const val ESSENTIAL_16 = 0x8000
private const val MAX_INDEX_8 = 0x7F
private const val MAX_INDEX_16 = 0x7FFF
private const val ANGLE_MASK = 0x03
private const val SHORT_SIZE = 2

/** Adds the rotation property to a HEIC that has none, and checks that nothing else changed (#71). */
internal object HeicRebuild {

    /**
     * [file] with an `irot` of [angle] added to its primary item and [edits] made in place, or null when that is not
     * safe: a box whose size field cannot grow, no association entry for the item, an index that no longer fits, or
     * data that lies past the first inserted byte and would have to move.
     */
    fun insert(file: ByteArray, layout: HeicLayout, angle: Int, edits: List<HeicEdit>): ByteArray? {
        val properties = layout.properties ?: return null
        val primary = properties.primary
        val index = properties.propertyCount + 1
        val boxes = listOf(properties.meta, properties.iprp, properties.ipco, properties.ipma)
        val fits = index <= (if (primary.wide) MAX_INDEX_16 else MAX_INDEX_8)
        val inserts = listOf(properties.ipco.end to irotBox(angle), primary.end to association(index, primary.wide))
            .sortedBy { it.first }
        val safe = fits && boxes.all { file.u32(it.start, true) == it.end - it.start } &&
            dataStaysPut(file, layout, inserts.first().first)
        return if (safe) withEdits(file, properties, inserts, edits) else null
    }

    private fun withEdits(
        file: ByteArray,
        properties: HeicProperties,
        inserts: List<Pair<Int, ByteArray>>,
        edits: List<HeicEdit>,
    ): ByteArray {
        val out = withInserted(file, inserts)
        fun shifted(at: Int) = at + inserts.filter { it.first <= at }.sumOf { it.second.size }
        val added = inserts.sumOf { it.second.size }
        val associationSize = inserts.first { it.second.size != IROT_BOX_SIZE }.second.size
        for ((box, grows) in listOf(
            properties.ipco to IROT_BOX_SIZE, properties.ipma to associationSize,
            properties.iprp to added, properties.meta to added,
        )) {
            out.putU32(shifted(box.start), file.u32(box.start, true) + grows)
        }
        val countAt = properties.primary.countAt
        out[shifted(countAt)] = (file[countAt] + 1).toByte()
        edits.forEach { it.bytes.copyInto(out, shifted(it.offset)) }
        return out
    }

    /** Every byte of data that a file offset names lies before the first insertion, so no offset has to change. */
    private fun dataStaysPut(file: ByteArray, layout: HeicLayout, firstInsertion: Int): Boolean {
        return layout.extents.isNotEmpty() && layout.extents.all { extent ->
            val range = extent.range(file, layout.idat)
            range != null && (extent.method != HEIC_METHOD_FILE || range.last < firstInsertion)
        }
    }

    private fun withInserted(file: ByteArray, inserts: List<Pair<Int, ByteArray>>): ByteArray {
        val out = ByteArray(file.size + inserts.sumOf { it.second.size })
        var from = 0
        var to = 0
        for ((at, bytes) in inserts) {
            file.copyInto(out, to, from, at)
            to += at - from
            bytes.copyInto(out, to)
            to += bytes.size
            from = at
        }
        file.copyInto(out, to, from, file.size)
        return out
    }

    /**
     * Whether [new] is [old] with an `irot` of [angle] added and nothing else changed: it reads as a HEIC, its primary
     * item has that irot, its Exif says the matching orientation, and every extent holds the same bytes as before.
     */
    fun isSound(old: ByteArray, new: ByteArray, angle: Int): Boolean = try {
        val before = HeicLayout.of(old)
        val after = HeicLayout.of(new)
        val irotAt = after.irotAt
        irotAt != null && new.u8(irotAt) and ANGLE_MASK == angle && exifMatches(before, after, new, angle) &&
            sameData(old, before, new, after)
    } catch (ignored: HeicRefusal) {
        false
    }

    private fun exifMatches(before: HeicLayout, after: HeicLayout, new: ByteArray, angle: Int): Boolean {
        val expected = before.exif
        if (expected !is ExifTarget.At) return after.exif == expected
        val written = after.exif as? ExifTarget.At ?: return false
        val shown = new.copyOfRange(written.offset, written.offset + SHORT_SIZE)
        return shown.contentEquals(HeicTurn.exifBytes(written, angle))
    }

    private fun sameData(old: ByteArray, before: HeicLayout, new: ByteArray, after: HeicLayout): Boolean {
        val was = withoutOrientation(old, before.exif)
        val now = withoutOrientation(new, after.exif)
        return before.extents.size == after.extents.size && before.extents.indices.all { i ->
            val inOld = before.extents[i].range(old, before.idat)
            val inNew = after.extents[i].range(new, after.idat)
            inOld != null && inNew != null && was.slice(inOld) == now.slice(inNew)
        }
    }

    /** [file] with the Exif orientation value cleared: the one place inside an item that a turn rewrites. */
    private fun withoutOrientation(file: ByteArray, exif: ExifTarget): ByteArray = file.copyOf().also {
        if (exif is ExifTarget.At) it.fill(0, exif.offset, exif.offset + SHORT_SIZE)
    }
}

private fun irotBox(angle: Int): ByteArray =
    int32Bytes(IROT_BOX_SIZE) + "irot".toByteArray() + byteArrayOf(angle.toByte())

private fun association(index: Int, wide: Boolean): ByteArray = if (wide) {
    val value = ESSENTIAL_16 or index
    byteArrayOf((value shr Byte.SIZE_BITS).toByte(), value.toByte())
} else {
    byteArrayOf((ESSENTIAL_8 or index).toByte())
}

private fun int32Bytes(value: Int) = ByteArray(Int.SIZE_BYTES).also { it.putU32(0, value) }
