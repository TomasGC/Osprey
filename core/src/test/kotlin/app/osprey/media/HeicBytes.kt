package app.osprey.media

private const val EXIF_PREFIX_SIZE = 6 // "Exif" and two zero bytes: what the exif_tiff_header_offset skips
private const val TIFF_ORIENTATION_VALUE_AT = 42 // where JpegBytes.tiff() puts the Orientation value in its block
private const val TILE_BYTES = 16
private const val HEADER = 8
private const val LARGE_HEADER = 16
private const val FULL_BOX_HEADER = 4
private const val ESSENTIAL_1 = 0x80
private const val ESSENTIAL_2 = 0x8000
private const val MISSING_PROPERTY = 99
private const val EXIF_ID = 4
private const val PRIMARY_ID = 1

/** A built HEIC and where the bytes a turn must change sit in it, known from construction, never from the parser. */
internal class BuiltHeic(val bytes: ByteArray, val irotAt: Int?, val exifValueAt: Int?)

/** Rare structural variants of a HEIC, each one a branch of the parser a real file seldom takes. */
internal class Quirks(
    val emptyIrot: Boolean = false,
    val pitmVersion: Int = 0,
    val iinfVersion: Int = 0,
    val infeVersion: Int = 2,
    val legacyTileInfe: Boolean = false,
    val ilocRows: List<Int>? = null,
    val twoExtents: Boolean = false,
    val noExtents: Boolean = false,
    val idatMissing: Boolean = false,
    val exifMethod: Int? = null,
    val exifTiffOffset: Int = EXIF_PREFIX_SIZE,
    val exifExtentLength: Int? = null,
    val ilocOffsetSize: Int = 4,
    val largeMeta: Boolean = false,
)

internal fun heicBox(type: String, payload: ByteArray = ByteArray(0)): ByteArray =
    int32(HEADER + payload.size) + type.toByteArray(Charsets.US_ASCII) + payload

internal fun heicFullBox(type: String, version: Int, flags: Int, payload: ByteArray): ByteArray =
    heicBox(type, byteArrayOf(version.toByte(), (flags shr 16).toByte(), (flags shr 8).toByte(), flags.toByte()) + payload)

private fun int16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())

private fun int64(value: Int): ByteArray = ByteArray(4) + int32(value)

private fun fixed(value: Int, size: Int): ByteArray = when (size) {
    4 -> int32(value)
    8 -> int64(value)
    else -> ByteArray(size)
}

private fun List<ByteArray>.joined(): ByteArray = fold(ByteArray(0)) { acc, part -> acc + part }

/**
 * A HEIC the way a phone writes it: a `grid` primary item (id 1) over two `hvc1` tiles (ids 2 and 3) and, unless
 * [exif] is false, an `Exif` item (id 4). [primaryIrot] and [tileIrot] are `irot` angles (null: no such property),
 * [exifOrientation] null means the Exif has no Orientation entry. The tiles and the Exif sit in `mdat` (the Exif in
 * `idat` when [exifInIdat]); `meta` comes after `mdat` as on a Samsung unless [metaFirst]. [quirks] holds the rare
 * variants.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
internal fun heic(
    primaryIrot: Int? = 3,
    tileIrot: Int? = null,
    mirror: Boolean = false,
    twoPrimaryIrot: Boolean = false,
    badIpmaIndex: Boolean = false,
    exif: Boolean = true,
    exifOrientation: Int? = 6,
    exifBigEndian: Boolean = false,
    exifInIdat: Boolean = false,
    ipmaVersion: Int = 0,
    ipmaFlags: Int = 0,
    ilocVersion: Int = 1,
    metaFirst: Boolean = false,
    largeMdat: Boolean = false,
    trailer: ByteArray = ByteArray(0),
    quirks: Quirks = Quirks(),
): BuiltHeic {
    require(!(exifInIdat && ilocVersion == 0)) { "iloc version 0 has no construction method" }
    val ftyp = heicBox("ftyp", "heic".toByteArray() + int32(0) + "mif1heic".toByteArray())

    // Properties, numbered from 1 in ipco; the start of each inside ipco's payload is remembered.
    val props = mutableListOf<ByteArray>()
    fun addProp(type: String, payload: ByteArray): Int {
        props += heicBox(type, payload)
        return props.size
    }
    val ispe = addProp("ispe", ByteArray(12))
    val hvcc = addProp("hvcC", ByteArray(8))
    val primaryIrotIndex = primaryIrot?.let { addProp("irot", if (quirks.emptyIrot) ByteArray(0) else byteArrayOf(it.toByte())) }
    val secondIrotIndex = if (twoPrimaryIrot) addProp("irot", byteArrayOf(1)) else null
    val mirrorIndex = if (mirror) addProp("imir", byteArrayOf(0)) else null
    val tileIrotIndex = tileIrot?.let { addProp("irot", byteArrayOf(it.toByte())) }
    val ipco = heicBox("ipco", props.joined())

    fun association(index: Int, essential: Boolean) = if (ipmaFlags and 1 == 1) {
        int16((if (essential) ESSENTIAL_2 else 0) or index)
    } else {
        byteArrayOf(((if (essential) ESSENTIAL_1 else 0) or index).toByte())
    }

    fun entry(itemId: Int, indexes: List<Pair<Int, Boolean>>): ByteArray {
        val id = if (ipmaVersion == 0) int16(itemId) else int32(itemId)
        return id + byteArrayOf(indexes.size.toByte()) + indexes.map { (i, e) -> association(i, e) }.joined()
    }
    val primaryIndexes = listOfNotNull(
        ispe to false, hvcc to false, primaryIrotIndex?.let { it to true }, secondIrotIndex?.let { it to true },
        mirrorIndex?.let { it to true }, if (badIpmaIndex) MISSING_PROPERTY to false else null,
    )
    val tileIndexes = listOfNotNull(ispe to false, hvcc to false, tileIrotIndex?.let { it to true })
    val entries = listOf(entry(PRIMARY_ID, primaryIndexes), entry(2, tileIndexes), entry(3, tileIndexes))
    val ipma = heicFullBox("ipma", ipmaVersion, ipmaFlags, int32(entries.size) + entries.joined())
    val iprp = heicBox("iprp", ipco + ipma)

    fun infe(id: Int, type: String, legacy: Boolean = false): ByteArray = when {
        legacy -> heicFullBox("infe", 1, 0, int16(id) + int16(0) + byteArrayOf(0) + byteArrayOf(0))
        quirks.infeVersion == 3 -> heicFullBox("infe", 3, 0, int32(id) + int16(0) + type.toByteArray() + byteArrayOf(0))
        else -> heicFullBox("infe", 2, 0, int16(id) + int16(0) + type.toByteArray() + byteArrayOf(0))
    }
    val infes = listOf(infe(PRIMARY_ID, "grid"), infe(2, "hvc1", quirks.legacyTileInfe), infe(3, "hvc1", quirks.legacyTileInfe)) +
        if (exif) listOf(infe(EXIF_ID, "Exif")) else emptyList()
    val itemCount = if (quirks.iinfVersion == 0) int16(infes.size) else int32(infes.size)
    val iinf = heicFullBox("iinf", quirks.iinfVersion, 0, itemCount + infes.joined())
    val pitm = heicFullBox("pitm", quirks.pitmVersion, 0, if (quirks.pitmVersion == 0) int16(PRIMARY_ID) else int32(PRIMARY_ID))
    val hdlr = heicFullBox("hdlr", 0, 0, int32(0) + "pict".toByteArray() + ByteArray(12) + byteArrayOf(0))

    val exifBytes = int32(quirks.exifTiffOffset) + "Exif".toByteArray() + byteArrayOf(0, 0) +
        tiff(exifBigEndian, exifOrientation, TYPE_SHORT, 1, null)
    val idat = if (exif && exifInIdat && !quirks.idatMissing) heicBox("idat", exifBytes) else ByteArray(0)
    val mdatPayload = ByteArray(TILE_BYTES) { 0xAA.toByte() } + ByteArray(TILE_BYTES) { 0xBB.toByte() } +
        if (exif && !exifInIdat) exifBytes else ByteArray(0)

    fun iloc(mdatPayloadAt: Int): ByteArray {
        val offsetSize = quirks.ilocOffsetSize
        fun row(id: Int, method: Int, offset: Int, length: Int, extents: Int = 1): ByteArray {
            val idBytes = if (ilocVersion == 2) int16(0) + int16(id) else int16(id)
            val methodBytes = if (ilocVersion == 0) ByteArray(0) else int16(method)
            val one = fixed(offset, offsetSize) + int32(length)
            val body = when (extents) {
                0 -> ByteArray(0)
                else -> one + if (extents == 2) fixed(0, offsetSize) + int32(1) else ByteArray(0)
            }
            return idBytes + methodBytes + int16(0) + int16(extents) + body
        }
        val rows = mutableListOf(row(2, 0, mdatPayloadAt, TILE_BYTES), row(3, 0, mdatPayloadAt + TILE_BYTES, TILE_BYTES))
        if (exif) {
            val length = quirks.exifExtentLength ?: exifBytes.size
            val extents = if (quirks.noExtents) 0 else if (quirks.twoExtents) 2 else 1
            val method = quirks.exifMethod ?: if (exifInIdat) 1 else 0
            val offset = if (exifInIdat) 0 else mdatPayloadAt + 2 * TILE_BYTES
            rows += row(EXIF_ID, method, offset, length, extents)
        }
        val kept = quirks.ilocRows?.let { ids -> rows.filterIndexed { i, _ -> (i + 2).coerceAtMost(EXIF_ID) in ids } } ?: rows
        val count = if (ilocVersion == 2) int32(kept.size) else int16(kept.size)
        val sizes = byteArrayOf(((offsetSize shl 4) or 4).toByte(), 0x00)
        return heicFullBox("iloc", ilocVersion, 0, sizes + count + kept.joined())
    }

    fun metaOf(mdatPayloadAt: Int): ByteArray {
        val payload = hdlr + pitm + iinf + iloc(mdatPayloadAt) + iprp + idat
        if (!quirks.largeMeta) return heicFullBox("meta", 0, 0, payload)
        val inner = byteArrayOf(0, 0, 0, 0) + payload
        return int32(1) + "meta".toByteArray() + int64(LARGE_HEADER + inner.size) + inner
    }
    val mdatHeader = if (largeMdat) LARGE_HEADER else HEADER
    val mdat = if (largeMdat) {
        int32(1) + "mdat".toByteArray() + int64(LARGE_HEADER + mdatPayload.size) + mdatPayload
    } else {
        heicBox("mdat", mdatPayload)
    }

    val mdatAt = if (metaFirst) ftyp.size + metaOf(0).size else ftyp.size
    val mdatPayloadAt = mdatAt + mdatHeader
    val meta = metaOf(mdatPayloadAt)
    val metaAt = if (metaFirst) ftyp.size else mdatAt + mdat.size
    val bytes = if (metaFirst) ftyp + meta + mdat + trailer else ftyp + mdat + meta + trailer

    val metaPayloadAt = metaAt + (if (quirks.largeMeta) LARGE_HEADER else HEADER) + FULL_BOX_HEADER
    val beforeIprp = hdlr.size + pitm.size + iinf.size + iloc(0).size
    val ipcoPayloadAt = metaPayloadAt + beforeIprp + HEADER + HEADER
    val irotAt = primaryIrotIndex?.let { ipcoPayloadAt + props.take(it - 1).sumOf { p -> p.size } + HEADER }
    val exifAt = if (exifInIdat) metaPayloadAt + beforeIprp + iprp.size + HEADER else mdatPayloadAt + 2 * TILE_BYTES
    val exifValueAt = if (exif && exifOrientation != null) exifAt + 4 + EXIF_PREFIX_SIZE + TIFF_ORIENTATION_VALUE_AT else null
    return BuiltHeic(bytes, irotAt, exifValueAt)
}
