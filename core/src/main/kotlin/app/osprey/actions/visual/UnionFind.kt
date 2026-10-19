package app.osprey.actions.visual

/**
 * Union-find (disjoint-set) over arbitrary keys, with path compression on [find]. An unseen key
 * is its own root. Direct port of the POC's `UnionFind` — trivial, no behavior change.
 */
class UnionFind<T> {

    private val parent = mutableMapOf<T, T>()

    fun find(x: T): T {
        var current = x
        parent.putIfAbsent(current, current)
        while (parent.getValue(current) != current) {
            parent[current] = parent.getValue(parent.getValue(current))
            current = parent.getValue(current)
        }
        return current
    }

    fun union(a: T, b: T) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA != rootB) {
            parent[rootA] = rootB
        }
    }
}
