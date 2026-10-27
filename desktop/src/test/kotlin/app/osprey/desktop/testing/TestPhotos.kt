package app.osprey.desktop.testing

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import javax.imageio.ImageIO

/** 8 bytes of PNG signature, then the 25-byte IHDR chunk. */
private const val PNG_SIGNATURE_AND_IHDR = 33
private const val TIFF_SIZE = 26
private const val EXIF_ORIENTATION_TAG = 0x0112
private const val TIFF_MAGIC: Short = 42
private const val IFD_OFFSET = 8
private const val SHORT_TYPE: Short = 3
private const val APP1_MARKER = 0xE1
private const val SEGMENT_LENGTH_BYTES = 2

/** This image as JPEG bytes, with an EXIF Orientation tag [orientation] (1-8) when one is given. */
fun BufferedImage.toJpeg(orientation: Int? = null): ByteArray {
    val jpeg = encoded("jpg")
    if (orientation == null) return jpeg
    val exif = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiffWithOrientation(orientation)
    val segment = byteArrayOf(0xFF.toByte(), APP1_MARKER.toByte(), 0, (exif.size + SEGMENT_LENGTH_BYTES).toByte()) + exif
    return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
}

/** This image as PNG bytes, with an `eXIf` chunk holding Orientation [orientation] (1-8) when one is given. */
fun BufferedImage.toPng(orientation: Int? = null): ByteArray {
    val png = encoded("png")
    if (orientation == null) return png
    val tiff = tiffWithOrientation(orientation)
    val type = "eXIf".toByteArray()
    val crc = CRC32().apply {
        update(type)
        update(tiff)
    }.value.toInt()
    val chunk = ByteBuffer.allocate(Int.SIZE_BYTES + type.size + tiff.size + Int.SIZE_BYTES)
        .putInt(tiff.size).put(type).put(tiff).putInt(crc).array()
    return png.copyOfRange(0, PNG_SIGNATURE_AND_IHDR) + chunk + png.copyOfRange(PNG_SIGNATURE_AND_IHDR, png.size)
}

private fun BufferedImage.encoded(format: String): ByteArray =
    ByteArrayOutputStream().also { ImageIO.write(this, format, it) }.toByteArray()

/** A TIFF block (little endian) holding one tag: Orientation = [value]. */
private fun tiffWithOrientation(value: Int): ByteArray = ByteBuffer.allocate(TIFF_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    .put("II".toByteArray()).putShort(TIFF_MAGIC).putInt(IFD_OFFSET) // header, IFD0 at offset 8
    .putShort(1) // one entry
    .putShort(EXIF_ORIENTATION_TAG.toShort()).putShort(SHORT_TYPE).putInt(1).putShort(value.toShort()).putShort(0)
    .putInt(0) // no next IFD
    .array()
