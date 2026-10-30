package app.osprey.media

private const val FLAGS_SIZE = 3
private const val SHORT_SIZE = 2
private const val INT_SIZE = 4
private const val FIRST_TYPED_INFE = 2
private const val LONG_ITEM_ID_FROM_INFE = 3
private const val WIDE_ASSOCIATIONS = 1
private const val PROPERTY_INDEX_16 = 0x7FFF
private const val PROPERTY_INDEX_8 = 0x7F
private const val EXIF_TYPE = "Exif"
private const val EXIF_TIFF_OFFSET_FIELD = 4
private const val ORIENTATION_VALUE_SIZE = 2
private const val TYPE_AT = 4
private const val TYPE_SIZE = 4

/** Where the Exif Orientation lives, if the photo has an Exif item at all. */
internal sealed interface ExifTarget {
    data object None : ExifTarget

    data object NoOrientation : ExifTarget

    class At(val offset: Int, val bigEndian: Boolean) : ExifTarget
}

/** The primary item's entry in an `ipma` box: its property [indexes], where its count byte is, where it ends. */
internal class HeicAssociations(val indexes: List<Int>, val countAt: Int, val end: Int, val wide: Boolean)

/** The boxes and offsets adding an `irot` to the primary item has to touch. */
internal class HeicProperties(
    val meta: HeicBox,
    val iprp: HeicBox,
    val ipco: HeicBox,
    val ipma: HeicBox,
    val primary: HeicAssociations,
    val propertyCount: Int,
)

/** What a turn needs to know about a HEIC: the `irot` of its primary item, if it is mirrored, its Exif, its data. */
internal class HeicLayout(
    val irotAt: Int?,
    val mirrored: Boolean,
    val exif: ExifTarget,
    val extents: List<HeicExtent>,
    val idat: HeicBox?,
    val properties: HeicProperties?,
) {

    companion object {
        /** The layout of [file], or why it is not a HEIC a turn can work on. */
        fun of(file: ByteArray): HeicLayout {
            val isHeif = file.size >= TYPE_AT + TYPE_SIZE &&
                String(file, TYPE_AT, TYPE_SIZE, Charsets.ISO_8859_1) == "ftyp"
            if (!isHeif) refuseHeic(HeicTurn.NOT_A_HEIC)
            val meta = boxesIn(file, 0, file.size).firstOrNull { it.type == "meta" } ?: refuseHeic(HeicTurn.NOT_A_HEIC)
            return read(file, meta)
        }

        private fun read(file: ByteArray, meta: HeicBox): HeicLayout {
            val children = boxesIn(file, meta.payloadAt + HEIC_FULL_BOX_HEADER, meta.end)
            fun child(type: String) = children.firstOrNull { it.type == type } ?: refuseHeic(HeicTurn.DAMAGED)
            val primary = primaryItem(file, child("pitm"))
            val found = propertiesOf(file, meta, child("iprp"), primary)
            val irots = found.chosen.filter { it.type == "irot" }
            if (irots.size > 1) refuseHeic(HeicTurn.DAMAGED)
            val irot = irots.firstOrNull()
            if (irot != null && irot.end == irot.payloadAt) refuseHeic(HeicTurn.DAMAGED)
            val idat = children.firstOrNull { it.type == "idat" }
            val extents = readExtents(file, child("iloc"))
            val exif = exifIn(file, child("iinf"), extents, idat)
            val mirrored = found.chosen.any { it.type == "imir" }
            return HeicLayout(irot?.payloadAt, mirrored, exif, extents, idat, found.properties)
        }

        private fun primaryItem(file: ByteArray, pitm: HeicBox): Long {
            val cursor = Cursor(file, pitm.payloadAt, pitm.end)
            val version = cursor.u8()
            cursor.skip(FLAGS_SIZE)
            return if (version == 0) cursor.u16().toLong() else cursor.u32()
        }

        private class Found(val chosen: List<HeicBox>, val properties: HeicProperties?)

        /** The property boxes of the primary item; an association to a property that is not there is damage. */
        private fun propertiesOf(file: ByteArray, meta: HeicBox, iprp: HeicBox, primary: Long): Found {
            val inside = boxesIn(file, iprp.payloadAt, iprp.end)
            val ipco = inside.firstOrNull { it.type == "ipco" } ?: refuseHeic(HeicTurn.DAMAGED)
            val properties = boxesIn(file, ipco.payloadAt, ipco.end)
            val entries = inside.filter { it.type == "ipma" }.map { it to associationsOf(file, it, primary) }
            val chosen = entries.flatMap { it.second?.indexes.orEmpty() }
                .map { properties.getOrNull(it - 1) ?: refuseHeic(HeicTurn.DAMAGED) }
            val first = entries.firstOrNull { it.second != null }
            val insertion = first?.let { (ipma, entry) ->
                HeicProperties(meta, iprp, ipco, ipma, entry!!, properties.size)
            }
            return Found(chosen, insertion)
        }

        private fun associationsOf(file: ByteArray, ipma: HeicBox, primary: Long): HeicAssociations? {
            val cursor = Cursor(file, ipma.payloadAt, ipma.end)
            val version = cursor.u8()
            val wide = cursor.unsigned(FLAGS_SIZE).toInt() and WIDE_ASSOCIATIONS == WIDE_ASSOCIATIONS
            var found: HeicAssociations? = null
            repeat(cursor.u32().toInt()) {
                val item = if (version == 0) cursor.u16().toLong() else cursor.u32()
                val countAt = cursor.at
                val indexes = List(cursor.u8()) {
                    if (wide) cursor.u16() and PROPERTY_INDEX_16 else cursor.u8() and PROPERTY_INDEX_8
                }
                if (item == primary) found = HeicAssociations(indexes, countAt, cursor.at, wide)
            }
            return found
        }

        private fun exifIn(file: ByteArray, iinf: HeicBox, extents: List<HeicExtent>, idat: HeicBox?): ExifTarget {
            val id = exifItem(file, iinf) ?: return ExifTarget.None
            val extent = extents.firstOrNull { it.item == id } ?: refuseHeic(HeicTurn.DAMAGED)
            return orientationIn(file, extent.range(file, idat) ?: refuseHeic(HeicTurn.DAMAGED))
        }

        private fun exifItem(file: ByteArray, iinf: HeicBox): Long? {
            val version = file.u8(iinf.payloadAt)
            val countSize = if (version == 0) SHORT_SIZE else INT_SIZE
            val entries = boxesIn(file, iinf.payloadAt + HEIC_FULL_BOX_HEADER + countSize, iinf.end)
            return entries.filter { it.type == "infe" }.firstNotNullOfOrNull { typedExifId(file, it) }
        }

        // An infe before version 2 names no type, so it cannot be the Exif item.
        private fun typedExifId(file: ByteArray, infe: HeicBox): Long? {
            val cursor = Cursor(file, infe.payloadAt, infe.end)
            val version = cursor.u8()
            cursor.skip(FLAGS_SIZE)
            if (version < FIRST_TYPED_INFE) return null
            val id = if (version < LONG_ITEM_ID_FROM_INFE) cursor.u16().toLong() else cursor.u32()
            cursor.skip(SHORT_SIZE)
            return id.takeIf { cursor.text() == EXIF_TYPE }
        }

        /** The Orientation value inside the Exif item at [extent], if it has one. */
        private fun orientationIn(file: ByteArray, extent: IntRange): ExifTarget {
            val cursor = Cursor(file, extent.first, extent.last + 1)
            val tiffStart = extent.first + EXIF_TIFF_OFFSET_FIELD + cursor.u32()
            if (tiffStart > extent.last) refuseHeic(HeicTurn.DAMAGED)
            val located = TiffOrientation.locate(file, tiffStart.toInt()) ?: return ExifTarget.NoOrientation
            if (located.offset + ORIENTATION_VALUE_SIZE > extent.last + 1) refuseHeic(HeicTurn.DAMAGED)
            return ExifTarget.At(located.offset, located.bigEndian)
        }
    }
}
