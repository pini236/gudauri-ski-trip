package io.github.pini236.skiapp.game

import io.github.pini236.skiapp.game.Merge.Dir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The site's rules (site/games/merge): one merge per tile per move, 2^(level+1) points, three undos, a full board ends it. */
class MergeTest {
    private fun levels(m: Merge) = List(Merge.SIZE) { r -> List(Merge.SIZE) { c -> m.grid[r][c]?.lv ?: -1 } }

    @Test fun twoOfAKindBecomeTheNextOnceAMove() {
        val m = Merge(Random(1))
        m.set(listOf(listOf(0, 0, 0, 0), listOf(1, -1, 1, -1), listOf(2, 2, -1, -1), listOf(-1, -1, -1, 11)))
        val mv = m.move(Dir.LEFT)!!
        val l = levels(m)
        // four flakes make two small balls, not one ball; across a gap they still meet; the king of Kazbek stays the king
        assertEquals(listOf(1, 1), l[0].take(2))
        assertEquals(2, l[1][0]); assertEquals(3, l[2][0]); assertEquals(11, l[3][0])
        assertEquals(4, mv.merges.size)
        assertEquals(4 + 4 + 8 + 16, mv.gained)
        assertEquals(mv.gained, m.score)
        assertNotNull(mv.spawned)
    }

    @Test fun nothingMovesNothingHappens() {
        val m = Merge(Random(2))
        m.set(listOf(listOf(0, 1, -1, -1), listOf(-1, -1, -1, -1), listOf(-1, -1, -1, -1), listOf(-1, -1, -1, -1)))
        assertNull(m.move(Dir.LEFT))
        assertNull(m.move(Dir.UP))
        assertNotNull(m.move(Dir.RIGHT))
    }

    @Test fun threeUndosAGame() {
        val m = Merge(Random(3))
        m.newGame()
        assertFalse(m.canUndo)
        var moves = 0
        for (d in listOf(Dir.LEFT, Dir.RIGHT, Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT, Dir.UP, Dir.DOWN)) if (m.move(d) != null) moves++
        assertTrue(moves >= 4)
        repeat(3) { assertTrue(m.undo()) }
        assertFalse("three undos a game", m.undo())
    }

    @Test fun aFullBoardWithNoPairsIsTheEnd() {
        val m = Merge(Random(4))
        m.set(listOf(listOf(0, 1, 0, 1), listOf(1, 0, 1, 0), listOf(0, 1, 0, 1), listOf(1, 0, 1, 0)))
        assertFalse(m.canMove())
        m.set(listOf(listOf(0, 1, 0, 1), listOf(1, 0, 1, 0), listOf(0, 1, 0, 1), listOf(1, 0, 1, 1)))
        assertTrue(m.canMove())
    }

    @Test fun theBoardIsKeptAndComesBack() {
        val m = Merge(Random(5))
        m.newGame(); m.move(Dir.LEFT); m.move(Dir.UP)
        val b = Merge(Random(6))
        assertTrue(b.restore(m.save()))
        assertEquals(levels(m), levels(b)); assertEquals(m.score, b.score); assertEquals(m.undos, b.undos)
        assertFalse(b.restore("{oops"))
    }
}
