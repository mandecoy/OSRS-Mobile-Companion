package com.mandecoy.osrscompanion;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

/** Regression checks: every published move must be a legal tile tap. */
public class FastPuzzleSolverTest {
    private static int[] scrambled(int depth, long seed) {
        int[] board = new int[25];
        for (int i = 0; i < 25; i++) board[i] = i;
        Random rng = new Random(seed);
        int blank = 24, last = -1;
        for (int n = 0; n < depth; n++) {
            int[] possibilities = new int[4];
            int num = 0;
            if (blank >= 5 && blank - 5 != last) possibilities[num++] = blank - 5;
            if (blank < 20 && blank + 5 != last) possibilities[num++] = blank + 5;
            if (blank % 5 > 0 && blank - 1 != last) possibilities[num++] = blank - 1;
            if (blank % 5 < 4 && blank + 1 != last) possibilities[num++] = blank + 1;
            if (num == 0) break;
            int next = possibilities[rng.nextInt(num)];
            board[blank] = board[next];
            board[next] = 24;
            last = blank;
            blank = next;
        }
        return board;
    }

    private static boolean adjacent(int x, int y) {
        return (x / 5 == y / 5 && Math.abs(x - y) == 1) || Math.abs(x - y) == 5;
    }

    private static void assertPathSolves(int[] start, int[] steps) {
        assertNotNull("Must solve a legal short scramble", steps);
        int[] board = start.clone();
        int blank = -1;
        for (int i = 0; i < 25; i++) if (board[i] == 24) blank = i;
        assertTrue(blank >= 0);
        for (int pos : steps) {
            assertTrue("Illegal tile tap " + pos + " from blank " + blank,
                pos >= 0 && pos < 25 && adjacent(blank, pos));
            board[blank] = board[pos];
            board[pos] = 24;
            blank = pos;
        }
        for (int i = 0; i < 25; i++)
            assertEquals("Tile incorrect after solver", i, board[i]);
    }

    @Test public void solverProducesLegalCompletePathsForScrambles() {
        for (int depth : new int[]{1, 2, 4, 8, 12, 18}) {
            for (long seed = 0; seed < 4; seed++) {
                int[] original = scrambled(depth, seed + 1421);
                int[] untouched = original.clone();
                assertPathSolves(original, FastPuzzleSolver.solve(original));
                assertArrayEquals("Solver must not mutate input", untouched, original);
            }
        }
    }

    @Test public void solvedBoardNeedsNoMoves() {
        int[] solved = new int[25];
        for (int i = 0; i < 25; i++) solved[i] = i;
        assertPathSolves(solved, FastPuzzleSolver.solve(solved));
    }
}
