package earth.cora.picnic.core.model

/*
 * The line diff behind commitAislesText (docs/DESIGN_STICKER.md §6.7). Pure. Owner: WP3 (AislesTextCommitTest).
 * Aisle ids are slugs of the names, so a plain re-parse would orphan every item of an edited line; this works out
 * which old aisle each new line is.
 */
internal object AisleDiff {
    /**
     * Old aisle id -> the id of the same aisle in [new], or null when its line was removed. Every old aisle is a
     * key. Matching, in order:
     * 1. the longest common subsequence of the names (unchanged lines);
     * 2. among the rest, an identical name elsewhere (a moved line), first come first served;
     * 3. between two matched lines (or the text's ends), the remaining old and new lines in that gap pair up in
     *    order: renames. Old lines left over are removed; new ones are added (not in the map).
     */
    fun idMap(old: List<Aisle>, new: List<Aisle>): Map<String, String?> {
        val n = old.size
        val m = new.size
        val oldToNew = IntArray(n) { -1 }
        val newTaken = BooleanArray(m)

        // 1. LCS on the names.
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (old[i].name == new[j].name) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val unchanged = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                old[i].name == new[j].name -> {
                    oldToNew[i] = j
                    newTaken[j] = true
                    unchanged += i to j
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> i++
                else -> j++
            }
        }

        // 2. Moved lines: the same name elsewhere.
        for (a in 0 until n) {
            if (oldToNew[a] >= 0) continue
            val b = (0 until m).firstOrNull { !newTaken[it] && new[it].name == old[a].name } ?: continue
            oldToNew[a] = b
            newTaken[b] = true
        }

        // 3. Renames: pair what is left inside each gap between LCS anchors, in order.
        val anchors = listOf(-1 to -1) + unchanged + (n to m)
        for (k in 0 until anchors.size - 1) {
            val (oldFrom, newFrom) = anchors[k]
            val (oldTo, newTo) = anchors[k + 1]
            val freeOld = (oldFrom + 1 until oldTo).filter { oldToNew[it] < 0 }
            val freeNew = (newFrom + 1 until newTo).filter { !newTaken[it] }
            for ((a, b) in freeOld.zip(freeNew)) {
                oldToNew[a] = b
                newTaken[b] = true
            }
        }

        val out = LinkedHashMap<String, String?>()
        for (a in 0 until n) out[old[a].id] = oldToNew[a].takeIf { it >= 0 }?.let { new[it].id }
        return out
    }
}
