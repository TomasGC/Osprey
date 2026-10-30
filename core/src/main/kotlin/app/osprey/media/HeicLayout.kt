package app.osprey.media

private const val FULL_BOX_HEADER = 4
private const val LONG_ITEM_ID_FROM_INFE = 3
private const val FIRST_TYPED_INFE = 2
private const val SHORT_SIZE = 2
private const val INT_SIZE = 4
private const val FLAGS_SIZE = 3
private const val METHOD_MASK = 0xF
private const val METHOD_FILE = 0
private const val METHOD_IDAT = 1
private const val WIDE_ASSOCIATIONS = 1
private const val PROPERTY_INDEX_16 = 0x7FFF
private const val PROPERTY_INDEX_8 = 0x7F
private const val EXIF_TYPE = "Exif"
private const val EXIF_TIFF_OFFSET_FIELD = 4
private const val ORIENTATION_VALUE_SIZE = 2

/** Where the Exif Orientation lives, if the photo has an Exif item at all. */
internal sealed interface ExifTarget {
    data object None : ExifTarget

    data object NoOrientation : ExifTarget

    class At(val offset: Int, val bigEndian: Boolean) : ExifTarget
}

/** What a turn needs to know about a HEIC: the `irot` byte of its primary item, whether it is mirrored, its Exif. */
internal class HeicLayout(val irotAt: Int?, val mirrored: Boolean, val exif: ExifTarget) {

    companion object {
        fun read(file: ByteArray, meta: HeicBox): HeicLayout {
            val children = boxesIn(file, meta.payloadAt + FULL_BOX_HEADER, meta.end)
            fun child(type: String) = children.firstOrNull { it.type == type } ?: refuseHeic(HeicTurn.DAMAGED)
            val primary = primaryItem(file, child("pitm"))
            val chosen = propertiesOf(file, child("iprp"), primary)
            val irots = chosen.filter { it.type == "irot" }
            if (irots.size > 1) refuseHeic(HeicTurn.DAMAGED)
            val irot = irots.firstOrNull()
            if (irot != null && irot.end == irot.payloadAt) refuseHeic(HeicTurn.DAMAGED)
            val exif = exifIn(file, child("iinf"), children)
            return HeicLayout(irot?.payloadAt, chosen.any { it.type == "imir" }, exif)
        }

        private fun primaryItem(file: ByteArray, pitm: HeicBox): Long {
            val cursor = Cursor(file, pitm.payloadAt, pitm.end)
            val version = cursor.u8()
            cursor.skip(FLAGS_SIZE)
            return if (version == 0) cursor.u16().toLong() else cursor.u32()
        }

        /** The property boxes associated with [primary]; an association to a property that is not there is damage. */
        private fun propertiesOf(file: ByteArray, iprp: HeicBox, primary: Long): List<HeicBox> {
            val inside = boxesIn(file, iprp.payloadAt, iprp.end)
            val ipco = inside.firstOrNull { it.type == "ipco" } ?: refuseHeic(HeicTurn.DAMAGED)
            val properties = boxesIn(file, ipco.payloadAt, ipco.end)
            val indexes = inside.filter { it.type == "ipma" }.flatMap { associationsOf(file, it, primary) }
            return indexes.map { properties.getOrNull(it - 1) ?: refuseHeic(HeicTurn.DAMAGED) }
        }

        private fun associationsOf(file: ByteArray, ipma: HeicBox, primary: Long): List<Int> {
            val cursor = Cursor(file, ipma.payloadAt, ipma.end)
            val version = cursor.u8()
            val wide = cursor.unsigned(FLAGS_SIZE).toInt() and WIDE_ASSOCIATIONS == WIDE_ASSOCIATIONS
            val found = mutableListOf<Int>()
            repeat(cursor.u32().toInt()) {
                val item = if (version == 0) cursor.u16().toLong() else cursor.u32()
                repeat(cursor.u8()) {
                    val index = if (wide) cursor.u16() and PROPERTY_INDEX_16 else cursor.u8() and PROPERTY_INDEX_8
                    if (item == primary) found += index
                }
            }
            return found
        }

        private fun exifIn(file: ByteArray, iinf: HeicBox, children: List<HeicBox>): ExifTarget {
            val id = exifItem(file, iinf) ?: return ExifTarget.None
            val iloc = children.firstOrNull { it.type == "iloc" } ?: refuseHeic(HeicTurn.DAMAGED)
            val idat = children.firstOrNull { it.type == "idat" }
            val extent = extentOf(file, iloc, id, idat) ?: refuseHeic(HeicTurn.DAMAGED)
            return orientationIn(file, extent)
        }

        private fun exifItem(file: ByteArray, iinf: HeicBox): Long? {
            val version = file.u8(iinf.payloadAt)
            val countSize = if (version == 0) SHORT_SIZE else INT_SIZE
            val entries = boxesIn(file, iinf.payloadAt + FULL_BOX_HEADER + countSize, iinf.end)
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

        /** Where item [id]'s first extent is in the file (its range), or null when iloc does not list it. */
        private fun extentOf(file: ByteArray, iloc: HeicBox, id: Long, idat: HeicBox?): IntRange? {
            val cursor = Cursor(file, iloc.payloadAt, iloc.end)
            val version = cursor.u8()
            cursor.skip(FLAGS_SIZE)
            val sizes = cursor.u8()
            val baseAndIndex = cursor.u8()
            val offsetSize = sizes shr Byte.SIZE_BITS / 2
            val lengthSize = sizes and METHOD_MASK
            val baseSize = baseAndIndex shr Byte.SIZE_BITS / 2
            val indexSize = if (version == 0) 0 else baseAndIndex and METHOD_MASK
            val items = if (version < 2) cursor.u16().toLong() else cursor.u32()
            repeat(items.toInt()) {
                val item = if (version < 2) cursor.u16().toLong() else cursor.u32()
                val method = if (version == 0) METHOD_FILE else cursor.u16() and METHOD_MASK
                cursor.skip(SHORT_SIZE)
                val base = cursor.unsigned(baseSize)
                var first: Pair<Long, Long>? = null
                repeat(cursor.u16()) {
                    cursor.skip(indexSize)
                    val extent = cursor.unsigned(offsetSize) to cursor.unsigned(lengthSize)
                    if (first == null) first = extent
                }
                if (item == id) return located(file, method, base, first ?: refuseHeic(HeicTurn.DAMAGED), idat)
            }
            return null
        }

        private fun located(
            file: ByteArray,
            method: Int,
            base: Long,
            extent: Pair<Long, Long>,
            idat: HeicBox?,
        ): IntRange {
            val (origin, limit) = when (method) {
                METHOD_FILE -> 0L to file.size.toLong()
                METHOD_IDAT -> (idat ?: refuseHeic(HeicTurn.DAMAGED)).let { it.payloadAt.toLong() to it.end.toLong() }
                else -> refuseHeic(HeicTurn.DAMAGED)
            }
            val start = origin + base + extent.first
            if (start + extent.second > limit) refuseHeic(HeicTurn.DAMAGED)
            return start.toInt() until (start + extent.second).toInt()
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
