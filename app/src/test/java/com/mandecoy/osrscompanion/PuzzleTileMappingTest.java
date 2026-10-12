package com.mandecoy.osrscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression tests for the actual phone correction UI operations. */
public class PuzzleTileMappingTest {
    private int[] solved() {
        int[] board = new int[25];
        for (int i = 0; i < 25; i++) board[i] = i;
        return board;
    }

    @Test public void all25TilesIncludingEmptyCanBeSelected() {
        for (int i = 0; i < 25; i++) {
            int[] edit = PuzzleTileMapping.assignTile(solved(), i, 24);
            assertEquals("EMPTY must be assignable anywhere", 24, edit[i]);
            assertEquals(i, edit[24]);
            assertTrue(PuzzleTileMapping.isPermutation(edit));
        }
    }

    @Test public void lastTileAndOtherTilesCanBeSwapped() {
        for (int pos = 0; pos < 25; pos++) {
            int[] result = PuzzleTileMapping.swapPositions(solved(), pos, 24);
            assertEquals(24, result[pos]);
            assertEquals(pos, result[24]);
            assertTrue(PuzzleTileMapping.isPermutation(result));
            assertArrayEquals(solved(), PuzzleTileMapping.swapPositions(result, pos, 24));
        }
    }

    @Test public void pictureTilesCanBeSwappedAndUndone() {
        int[] incorrect = PuzzleTileMapping.swapPositions(solved(), 1, 3);
        assertEquals(3, incorrect[1]);
        assertEquals(1, incorrect[3]);
        assertFalse(PuzzleTileMapping.isSolvable(incorrect));
        int[] fixed = PuzzleTileMapping.swapPositions(incorrect, 1, 3);
        assertArrayEquals(solved(), fixed);
        assertTrue(PuzzleTileMapping.isSolvable(fixed));
    }

    @Test public void assignmentChangesNeverModifyOriginalInput() {
        int[] original = solved();
        int[] a = PuzzleTileMapping.assignTile(original, 0, 23);
        assertEquals(23, a[0]);
        assertEquals(0, original[0]);
        assertTrue(PuzzleTileMapping.isPermutation(a));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidBoardCannotBeCorrected() {
        int[] bad = new int[25];
        PuzzleTileMapping.swapPositions(bad, 1, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotChooseOutsideGrid() {
        PuzzleTileMapping.assignTile(solved(), 24, 25);
    }
}
