package app.osprey.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

private const val DELTA = 0.001f

/** A 1000 x 500 view showing a 2000 x 1000 photo: fit scale 0.5, so it fills the view exactly at zoom 1. */
class ViewTransformTest {

    private val start = ViewTransform(viewWidth = 1000f, viewHeight = 500f, imageWidth = 2000f, imageHeight = 1000f)

    private fun assertPan(expectedX: Float, expectedY: Float, actual: ViewTransform) {
        assertEquals(expectedX, actual.panX, DELTA)
        assertEquals(expectedY, actual.panY, DELTA)
    }

    @Test
    fun `it starts at fit with no pan and no turn`() {
        assertEquals(1f, start.zoom, DELTA)
        assertPan(0f, 0f, start)
        assertEquals(0, start.quarterTurns)
        assertEquals(0.5f, start.scale, DELTA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a view or photo with no size is refused`() {
        ViewTransform(viewWidth = 0f, viewHeight = 500f, imageWidth = 2000f, imageHeight = 1000f)
    }

    @Test
    fun `zooming at the centre keeps the photo centred`() {
        val zoomed = start.zoomAt(500f, 250f, 2f)

        assertEquals(2f, zoomed.zoom, DELTA)
        assertEquals(1f, zoomed.scale, DELTA)
        assertPan(0f, 0f, zoomed)
    }

    @Test
    fun `zooming at a corner keeps that corner of the photo under the pointer`() {
        assertPan(500f, 250f, start.zoomAt(0f, 0f, 2f))
        assertPan(-500f, -250f, start.zoomAt(1000f, 500f, 2f))
    }

    @Test
    fun `zoom stays between fit and the maximum`() {
        assertEquals(MAX_ZOOM, start.zoomAt(500f, 250f, 100f).zoom, DELTA)
        assertEquals(1f, start.zoomAt(500f, 250f, 0.1f).zoom, DELTA)
    }

    @Test
    fun `a photo at fit cannot be panned`() {
        assertPan(0f, 0f, start.panBy(300f, 200f))
    }

    @Test
    fun `a zoomed photo pans freely up to its edges`() {
        val zoomed = start.zoomAt(500f, 250f, 2f)

        assertPan(100f, 50f, zoomed.panBy(100f, 50f))
        assertPan(500f, 250f, zoomed.panBy(10000f, 10000f))
        assertPan(-500f, -250f, zoomed.panBy(-10000f, -10000f))
    }

    @Test
    fun `zooming out brings the photo back inside the limits`() {
        val corner = start.zoomAt(0f, 0f, 2f)

        assertPan(0f, 0f, corner.zoomAt(500f, 250f, 0.5f))
    }

    @Test
    fun `an axis where the photo is smaller than the view stays centred`() {
        val tall = ViewTransform(viewWidth = 1000f, viewHeight = 500f, imageWidth = 500f, imageHeight = 1000f)
        val zoomed = tall.zoomAt(500f, 250f, 2f)

        assertPan(0f, 100f, zoomed.panBy(100f, 100f))
        assertPan(0f, 250f, zoomed.panBy(100f, 10000f))
    }

    @Test
    fun `the wheel zooms in when it scrolls up and out when it scrolls down`() {
        assertEquals(WHEEL_ZOOM_STEP, start.zoomByWheel(500f, 250f, -1f).zoom, DELTA)
        assertEquals(WHEEL_ZOOM_STEP * WHEEL_ZOOM_STEP, start.zoomByWheel(500f, 250f, -2f).zoom, DELTA)
        assertEquals(1f, start.zoomByWheel(500f, 250f, 1f).zoom, DELTA)
    }

    @Test
    fun `a double tap zooms in at the point, a second one goes back to fit`() {
        val zoomed = start.doubleTap(0f, 0f)

        assertEquals(DOUBLE_TAP_ZOOM, zoomed.zoom, DELTA)
        assertPan(1000f, 500f, zoomed)
        assertEquals(start, zoomed.doubleTap(10f, 10f))
    }

    @Test
    fun `a double tap on a pinch-zoomed photo also goes back to fit`() {
        assertEquals(start, start.zoomAt(500f, 250f, 1.4f).doubleTap(500f, 250f))
    }

    @Test
    fun `turning right and left cycles the four quarter turns`() {
        assertEquals(1, start.rotateRight().quarterTurns)
        assertEquals(2, start.rotateRight().rotateRight().quarterTurns)
        assertEquals(0, start.rotateRight().rotateRight().rotateRight().rotateRight().quarterTurns)
        assertEquals(3, start.rotateLeft().quarterTurns)
        assertEquals(0, start.rotateLeft().rotateRight().quarterTurns)
    }

    @Test
    fun `an odd number of turns fits the photo sideways`() {
        assertEquals(0.5f, start.scale, DELTA)
        assertEquals(0.25f, start.rotateRight().scale, DELTA)
        assertEquals(0.5f, start.rotateRight().rotateRight().scale, DELTA)
    }

    @Test
    fun `turning goes back to fit but keeps the turn`() {
        val turned = start.zoomAt(0f, 0f, 3f).rotateRight()

        assertEquals(1f, turned.zoom, DELTA)
        assertPan(0f, 0f, turned)
        assertEquals(1, turned.quarterTurns)
    }

    @Test
    fun `reset goes back to fit but keeps the turn`() {
        val reset = start.rotateRight().zoomAt(0f, 0f, 3f).reset()

        assertEquals(1f, reset.zoom, DELTA)
        assertPan(0f, 0f, reset)
        assertEquals(1, reset.quarterTurns)
    }

    @Test
    fun `resizing the view keeps the pan inside the new limits`() {
        val corner = start.zoomAt(0f, 0f, 2f)
        val smaller = corner.resizedTo(500f, 250f)

        assertEquals(500f, smaller.viewWidth, DELTA)
        assertEquals(250f, smaller.viewHeight, DELTA)
        assertPan(250f, 125f, smaller)
    }
}
