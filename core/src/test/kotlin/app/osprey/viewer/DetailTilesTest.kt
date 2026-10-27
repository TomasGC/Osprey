package app.osprey.viewer

import app.osprey.media.PhotoRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BASE = 2048
private const val SMALL_BASE = 100

class DetailTilesTest {

    @Test
    fun `at fit the base picture is enough`() =
        assertEquals(emptyList<DetailTile>(), ViewTransform(1000f, 1000f, 8000f, 6000f).detailTiles(BASE))

    @Test
    fun `a photo no larger than the base has no detail`() =
        assertEquals(
            emptyList<DetailTile>(),
            ViewTransform(1000f, 1000f, 2000f, 1500f, zoom = MAX_ZOOM).detailTiles(BASE),
        )

    @Test
    fun `at real pixels the tiles around the centre are decoded one to one, centre first, inside the photo`() {
        val tiles = ViewTransform(1000f, 1000f, 8000f, 6000f, zoom = MAX_ZOOM).detailTiles(BASE)

        assertEquals(16, tiles.size)
        assertTrue(tiles.all { it.sampleSize == 1 })
        assertTrue(tiles.all { it.region.left + it.region.width <= 8000 && it.region.top + it.region.height <= 6000 })
        assertEquals(PhotoRegion(3584, 2560, 512, 512), tiles.first().region)
    }

    @Test
    fun `a coarser zoom decodes coarser, larger tiles`() {
        val tiles = ViewTransform(1000f, 1000f, 16000f, 12000f, zoom = MAX_ZOOM).detailTiles(BASE)

        assertEquals(16, tiles.size)
        assertTrue(tiles.all { it.sampleSize == 2 })
        assertTrue(tiles.all { it.region.width == 1024 && it.region.height == 1024 && it.region.left % 1024 == 0 })
    }

    @Test
    fun `a zoom that the base still covers has no detail`() =
        // A 16000 px photo has base step 4 (4000 px): sample 4 is not finer than the base.
        assertEquals(
            emptyList<DetailTile>(),
            ViewTransform(1000f, 1000f, 16000f, 12000f, zoom = 4f).detailTiles(BASE),
        )

    @Test
    fun `tiles at the photo edge are clipped to it`() {
        val tiles = ViewTransform(1000f, 800f, 1000f, 800f, zoom = 2f, panX = -500f, panY = -400f)
            .detailTiles(SMALL_BASE)

        assertEquals(4, tiles.size)
        assertEquals(PhotoRegion(512, 512, 488, 288), tiles.first().region)
    }

    @Test
    fun `tiles in view come before the tiles that only add margin`() {
        val tiles = ViewTransform(1000f, 1000f, 8000f, 6000f, zoom = MAX_ZOOM).detailTiles(BASE)

        // In view: columns 6 to 8 and rows 4 to 6, a 3 x 3 block. Column 9 and row 7 are margin only.
        assertEquals(16, tiles.size)
        assertTrue(tiles.take(9).all { it.region.left in 3072..4096 && it.region.top in 2048..3072 })
        assertTrue(tiles.drop(9).none { it.region.left in 3072..4096 && it.region.top in 2048..3072 })
    }

    @Test
    fun `margin tiles beside the view all come after the tiles in view`() {
        // A tall narrow view over a wide photo, panned so the margin reaches the tile above and the tiles beside:
        // in view is column 2, rows 5 to 7; the margin adds columns 1 and 3 and row 4.
        val tiles = ViewTransform(300f, 1000f, 2400f, 6000f, zoom = MAX_ZOOM, panY = -100f).detailTiles(BASE)

        assertEquals(12, tiles.size)
        assertTrue(tiles.take(3).all { it.region.left == 1024 && it.region.top in 2560..3584 })
        assertTrue(tiles.drop(3).none { it.region.left == 1024 && it.region.top in 2560..3584 })
    }

    @Test
    fun `a quarter turn maps the visible part back to the upright photo`() =
        assertEquals(
            PhotoRegion(0, 512, 512, 288),
            ViewTransform(800f, 1000f, 1000f, 800f, zoom = 2f, panX = 400f, panY = 500f, quarterTurns = 1)
                .detailTiles(SMALL_BASE).first().region,
        )

    @Test
    fun `a half turn maps the visible part back to the upright photo`() =
        assertEquals(
            PhotoRegion(512, 512, 488, 288),
            ViewTransform(1000f, 800f, 1000f, 800f, zoom = 2f, panX = 500f, panY = 400f, quarterTurns = 2)
                .detailTiles(SMALL_BASE).first().region,
        )

    @Test
    fun `three quarter turns map the visible part back to the upright photo`() =
        assertEquals(
            PhotoRegion(512, 0, 488, 512),
            ViewTransform(800f, 1000f, 1000f, 800f, zoom = 2f, panX = 400f, panY = 500f, quarterTurns = 3)
                .detailTiles(SMALL_BASE).first().region,
        )
}
