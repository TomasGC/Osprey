package app.osprey.media

/**
 * The largest power-of-two subsampling step that still leaves the longest side of a
 * [width] x [height] image at [maxSide] pixels or more (1 = decode every pixel). Platform decoders
 * subsample in whole steps, so the result lands between [maxSide] and twice [maxSide], and an image
 * already within [maxSide] is never touched.
 */
fun subsampleStepFor(width: Int, height: Int, maxSide: Int): Int {
    val longest = maxOf(width, height)
    var step = 1
    while (longest / (step * 2) >= maxSide) step *= 2
    return step
}
