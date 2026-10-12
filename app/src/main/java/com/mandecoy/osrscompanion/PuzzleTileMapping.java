package com.mandecoy.osrscompanion;

/**
 * Pure, testable tile-assignment edits for a 5x5 sliding puzzle.
 *
 * A tile's value is its destination in the SOLVED puzzle (0..23), and
 * value 24 is the empty space. Labels are unique and never discarded.
 * These are edits to screenshot RECOGNITION, not moves made in OSRS.
 */
public final class PuzzleTileMapping {
    public static final int SIZE = 25;
    public static final int EMPTY = 24;

    private PuzzleTileMapping() {}

    public static boolean isPermutation(int[] board) {
        if (board == null || board.length != SIZE) return false;
        boolean[] seen = new boolean[SIZE];
        for (int tile : board) {
            if (tile < 0 || tile >= SIZE || seen[tile]) return false;
            seen[tile] = true;
        }
        return true;
    }

    public static int[] swapPositions(int[] input, int first, int second) {
        if (!isPermutation(input)) throw new IllegalArgumentException("Invalid board");
        if (first < 0 || first >= SIZE || second < 0 || second >= SIZE)
            throw new IllegalArgumentException("Position outside 5x5 grid");
        int[] board = input.clone();
        int temp = board[first];
        board[first] = board[second];
        board[second] = temp;
        return board;
    }

    /** Make the screenshot square at position contain the selected solved tile. */
    public static int[] assignTile(int[] input, int position, int tile) {
        if (!isPermutation(input)) throw new IllegalArgumentException("Invalid board");
        if (position < 0 || position >= SIZE || tile < 0 || tile >= SIZE)
            throw new IllegalArgumentException("Position or target outside 5x5 grid");
        int oldPosition = -1;
        for (int i = 0; i < SIZE; i++)
            if (input[i] == tile) { oldPosition = i; break; }
        return swapPositions(input, position, oldPosition);
    }

    /** For a 5x5 (odd-width) puzzle only inversion parity determines reachability. */
    public static boolean isSolvable(int[] board) {
        if (!isPermutation(board)) return false;
        int inversions = 0;
        for (int i = 0; i < SIZE; i++) {
            if (board[i] == EMPTY) continue;
            for (int j = i + 1; j < SIZE; j++)
                if (board[j] != EMPTY && board[i] > board[j]) inversions++;
        }
        return inversions % 2 == 0;
    }
}
