package app.osprey.actions.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UnionFindTest {

    @Test
    fun `union merges two elements`() {
        val uf = UnionFind<Int>()
        uf.union(1, 2)

        assertEquals(uf.find(1), uf.find(2))
    }

    @Test
    fun `union merges transitively`() {
        val uf = UnionFind<Int>()
        uf.union(1, 2)
        uf.union(2, 3)

        assertEquals(uf.find(1), uf.find(3))
    }

    @Test
    fun `unrelated elements stay apart`() {
        val uf = UnionFind<Int>()
        uf.union(1, 2)
        uf.find(3)

        assertNotEquals(uf.find(1), uf.find(3))
    }

    @Test
    fun `an unseen element is its own root`() {
        val uf = UnionFind<Int>()

        assertEquals(42, uf.find(42))
    }

    @Test
    fun `uniting two elements that already share a root changes nothing`() {
        val unionFind = UnionFind<Int>()
        unionFind.union(1, 2)
        val root = unionFind.find(1)

        unionFind.union(1, 2)
        unionFind.union(2, 2)

        assertEquals(root, unionFind.find(1))
        assertEquals(root, unionFind.find(2))
    }

    @Test
    fun `find compresses a long chain to one root`() {
        val unionFind = UnionFind<Int>()
        for (i in 1..5) unionFind.union(i, i + 1)

        val roots = (1..6).map { unionFind.find(it) }.toSet()

        assertEquals(1, roots.size)
    }
}
