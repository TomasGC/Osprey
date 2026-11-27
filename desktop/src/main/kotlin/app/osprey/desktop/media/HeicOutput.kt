package app.osprey.desktop.media

/** The picture format ffmpeg is asked for. BMP costs almost nothing to write and read, but only 8-bit survives it. */
enum class HeicOutput(val arguments: List<String>) {
    BMP(listOf("-vcodec", "bmp")),
    PNG(listOf("-vcodec", "png", "-compression_level", "1")),
}
