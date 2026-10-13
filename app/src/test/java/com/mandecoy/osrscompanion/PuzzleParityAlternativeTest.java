package com.mandecoy.osrscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

/** OCR ambiguity must never be mistaken for proof that a real OSRS clue puzzle is impossible. */
public class PuzzleParityAlternativeTest {
    @Test public void repairsOnlyTheLeastCostPicturePairAndKeepsGap() {
        int[] recognized = new int[25];
        for (int i=0; i<25; i++) recognized[i]=i;
        recognized[4]=6;
        recognized[6]=4;
        assertFalse(PuzzleTileMapping.isSolvable(recognized));
        double[][] costs = new double[25][25];
        for (int row=0; row<25; row++)
            for (int tile=0; tile<25; tile++)
                costs[row][tile] = (row == tile) ? 0.0 : 500.0;
        int[] candidate = PuzzleTileMapping.closestSolvableAlternative(recognized,costs);
        assertArrayEquals(new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24}, candidate);
        assertEquals(24, candidate[24]);
        assertTrue(PuzzleTileMapping.isSolvable(candidate));
        assertArrayEquals(new int[]{0,1,2,3,6,5,4,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24}, recognized);
    }

    @Test public void doesntChangeAlreadySolvableRecognition() {
        int[] board = new int[25];
        double[][] costs = new double[25][25];
        for (int i=0;i<25;i++) board[i]=i;
        int[] result = PuzzleTileMapping.closestSolvableAlternative(board,costs);
        assertArrayEquals(board,result);
        assertNotSame(board,result);
    }
}
