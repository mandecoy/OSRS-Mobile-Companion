/*
 * Sliding puzzle strategy adapted from RuneLite's Puzzle Solver (BSD 2-Clause).
 * Original work: RuneLite contributors.
 */
package com.mandecoy.osrscompanion;

import java.util.ArrayList;
import java.util.List;

public final class FastPuzzleSolver {
    private static final int D = 5;
    private static final int N = 25;
    private static final int GAP = 24;
    private static final int MAX_PRE_SWAPS = 1200;
    private static final int MAX_IDA_BOUND = 120;

    private FastPuzzleSolver() {}

    public static int[] solve(int[] input) {
        try {
            Engine engine = new Engine(input);
            if (!engine.preSolve()) return null;
            List<Integer> tail = idaSolve(engine.board);
            if (tail == null) return null;
            engine.moves.addAll(tail);

            int[] out = new int[engine.moves.size()];
            for (int i = 0; i < out.length; i++) out[i] = engine.moves.get(i);
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class P {
        final int x;
        final int y;
        P(int x, int y) { this.x = x; this.y = y; }
    }

    private enum Pattern {
        ROTATE_LEFT_UP(new int[]{1, -1, 0, -1, -1, -1, -1, 0}, 1, 1),
        ROTATE_LEFT_DOWN(null, 1, -1),
        ROTATE_RIGHT_UP(null, -1, 1),
        ROTATE_RIGHT_DOWN(null, -1, -1),
        ROTATE_UP_LEFT(new int[]{-1, 1, -1, 0, -1, -1, 0, -1}, 1, 1),
        ROTATE_UP_RIGHT(null, -1, 1),
        ROTATE_DOWN_LEFT(null, 1, -1),
        ROTATE_DOWN_RIGHT(null, -1, -1),
        LAST_PIECE_ROW(new int[]{-1, -1, 0, -1, -1, 0, -1, 1}, 1, 1),
        LAST_PIECE_COLUMN(new int[]{-1, -1, -1, 0, 0, -1, 1, -1}, 1, 1),
        SHUFFLE_UP_RIGHT(new int[]{1, -1, 0, -1}, 1, 1),
        SHUFFLE_UP_LEFT(new int[]{-1, -1, 0, -1}, 1, 1),
        SHUFFLE_UP_BELOW(new int[]{-1, 1, -1, 0}, 1, 1),
        SHUFFLE_UP_ABOVE(new int[]{-1, -1, -1, 0}, 1, 1);

        final int[] points;
        final int modX;
        final int modY;

        Pattern(int[] points, int modX, int modY) {
            this.points = points;
            this.modX = modX;
            this.modY = modY;
        }
    }

    private static final class Engine {
        int[] board;
        final ArrayList<Integer> moves = new ArrayList<>();
        int swaps = 0;

        Engine(int[] input) {
            board = input.clone();
        }

        boolean preSolve() {
            if (isGoal(board)) return true;
            solveRow(0);
            solveColumn0();
            solveRow(1);
            return swaps <= MAX_PRE_SWAPS;
        }

        void solveRow(int row) {
            for (int x = row; x < D; x++) {
                int target = row * D + x;
                if (pieceAt(x, row) != target) moveTowardsVal(target, x, row, true);
            }
        }

        void solveColumn0() {
            int column = 0;
            for (int y = 1; y < D; y++) {
                int target = column + y * D;
                if (pieceAt(column, y) != target) moveTowardsVal(target, column, y, false);
            }
        }

        void moveTowardsVal(int target, int x, int y, boolean rowMode) {
            boolean reached = false;
            int guard = 0;

            while (pieceAt(x, y) != target && guard++ < 500) {
                P val = find(target);
                P blank = find(GAP);

                if (reached) {
                    if (rowMode) {
                        alignTargetX(target, x, y);
                        swapUpRow(target, x, y);
                    } else {
                        alignTargetY(target, x, y);
                        swapLeftColumn(target, x, y);
                    }
                    continue;
                }

                int dx = val.x - blank.x;
                int dy = val.y - blank.y;
                int ax = Math.abs(dx);
                int ay = Math.abs(dy);

                if (dx == 0) {
                    if (ay == 1) {
                        reached = true;
                    } else if (dy >= 2) {
                        swap(blank, new P(blank.x, blank.y + 1));
                    } else if (dy <= -2) {
                        swap(blank, new P(blank.x, blank.y - 1));
                    }
                } else if (dy == 0) {
                    if (ax == 1) {
                        reached = true;
                    } else if (dx >= 2) {
                        swap(blank, new P(blank.x + 1, blank.y));
                    } else if (dx <= -2) {
                        swap(blank, new P(blank.x - 1, blank.y));
                    }
                } else if (rowMode) {
                    if (blank.y - 1 == y
                        && validRowValue(y, pieceAt(blank.x, blank.y - 1))
                        && pieceAt(blank.x, blank.y - 1) < target
                        && dy <= -1) {
                        swap(blank, new P(blank.x + 1, blank.y));
                        continue;
                    }
                    if (dy >= 1) swap(blank, new P(blank.x, blank.y + 1));
                    else swap(blank, new P(blank.x, blank.y - 1));
                } else {
                    if (blank.x - 1 == x
                        && validColumnValue(x, pieceAt(blank.x - 1, blank.y))
                        && pieceAt(blank.x - 1, blank.y) < target
                        && dx <= -1) {
                        swap(blank, new P(blank.x, blank.y + 1));
                        continue;
                    }
                    if (dx >= 1) swap(blank, new P(blank.x + 1, blank.y));
                    else swap(blank, new P(blank.x - 1, blank.y));
                }
            }
            if (guard >= 500) throw new IllegalStateException("Puzzle presolver stalled");
        }

        boolean validRowValue(int row, int value) {
            if (row == 0) return value >= 0 && value <= 4;
            if (row == 1) return value >= 6 && value <= 9;
            return false;
        }

        boolean validColumnValue(int column, int value) {
            return column == 0 && (value == 5 || value == 10 || value == 15 || value == 20);
        }

        void alignTargetX(int target, int x, int y) {
            P val = find(target);
            if (val.x == x) return;
            int direction = Integer.signum(x - val.x);
            int guard = 0;

            while (find(target).x != x && guard++ < 300) {
                val = find(target);
                P blank = find(GAP);
                if (x - val.x == 0) break;

                if (val.x == blank.x) {
                    int diff = blank.y - val.y;
                    if (diff == 1) {
                        P p1 = new P(blank.x + direction, blank.y);
                        P p2 = new P(p1.x, p1.y - 1);
                        swap(blank, p1); swap(p1, p2); swap(p2, val);
                    } else if (diff == -1) {
                        swap(blank, val);
                    }
                } else if (val.y == blank.y) {
                    int diff = blank.x - val.x;
                    if (diff == 1) {
                        if (direction == 1) {
                            swap(val, blank);
                        } else if (val.y == D - 1) {
                            perform(blank, val, Pattern.ROTATE_LEFT_UP);
                        } else {
                            perform(blank, val, Pattern.ROTATE_LEFT_DOWN);
                        }
                    } else if (diff == -1) {
                        if (direction == -1) {
                            swap(val, blank);
                        } else if (val.y == D - 1) {
                            perform(blank, val, Pattern.ROTATE_RIGHT_UP);
                        } else {
                            perform(blank, val, Pattern.ROTATE_RIGHT_DOWN);
                        }
                    }
                }
            }
            if (guard >= 300) throw new IllegalStateException("alignTargetX stalled");
        }

        void swapUpRow(int target, int x, int y) {
            P val = find(target);
            P blank = find(GAP);
            if (val.x == x && val.y == y) return;
            if (blank.x == x && blank.y == y && val.y - 1 == y) {
                swap(blank, val);
                return;
            }

            int guard = 0;
            while (guard++ < 300) {
                val = find(target);
                blank = find(GAP);
                if (val.x == x && val.y == y) return;

                if (val.x == blank.x) {
                    int diff = blank.y - val.y;
                    if (diff == 1) {
                        if (x == D - 1) {
                            perform(blank, val, Pattern.LAST_PIECE_ROW);
                            return;
                        }
                        perform(blank, val, Pattern.ROTATE_UP_RIGHT);
                    } else if (diff == -1) {
                        swap(blank, val);
                    }
                } else if (val.y == blank.y) {
                    int diff = blank.x - val.x;
                    if (diff == 1) {
                        perform(blank, val, Pattern.SHUFFLE_UP_RIGHT);
                    } else if (diff == -1) {
                        if (val.y - 1 == y) {
                            P p1 = new P(blank.x, blank.y + 1);
                            P p2 = new P(p1.x + 1, p1.y);
                            swap(blank, p1); swap(p1, p2);
                            continue;
                        }
                        perform(blank, val, Pattern.SHUFFLE_UP_LEFT);
                    }
                }
            }
            throw new IllegalStateException("swapUpRow stalled");
        }

        void alignTargetY(int target, int x, int y) {
            P val = find(target);
            if (val.y == y) return;
            int direction = Integer.signum(y - val.y);
            int guard = 0;

            while (find(target).y != y && guard++ < 300) {
                val = find(target);
                P blank = find(GAP);
                if (y - val.y == 0) break;

                if (val.y == blank.y) {
                    int diff = blank.x - val.x;
                    if (diff == 1) {
                        P p1 = new P(blank.x, blank.y + direction);
                        P p2 = new P(p1.x - 1, p1.y);
                        swap(blank, p1); swap(p1, p2); swap(p2, val);
                    } else if (diff == -1) {
                        swap(blank, val);
                    }
                } else if (val.x == blank.x) {
                    int diff = blank.y - val.y;
                    if (diff == 1) {
                        if (direction == 1) {
                            swap(val, blank);
                        } else if (val.x == D - 1) {
                            perform(blank, val, Pattern.ROTATE_UP_LEFT);
                        } else {
                            perform(blank, val, Pattern.ROTATE_UP_RIGHT);
                        }
                    } else if (diff == -1) {
                        if (direction == -1) {
                            swap(val, blank);
                        } else if (val.x == D - 1) {
                            perform(blank, val, Pattern.ROTATE_DOWN_LEFT);
                        } else {
                            perform(blank, val, Pattern.ROTATE_DOWN_RIGHT);
                        }
                    }
                }
            }
            if (guard >= 300) throw new IllegalStateException("alignTargetY stalled");
        }

        void swapLeftColumn(int target, int x, int y) {
            P val = find(target);
            P blank = find(GAP);
            if (val.x == x && val.y == y) return;
            if (blank.x == x && blank.y == y && val.x - 1 == x) {
                swap(blank, val);
                return;
            }

            int guard = 0;
            while (guard++ < 300) {
                val = find(target);
                blank = find(GAP);
                if (val.x == x && val.y == y) return;

                if (val.x == blank.x) {
                    int diff = blank.y - val.y;
                    if (diff == 1) {
                        perform(blank, val, Pattern.SHUFFLE_UP_BELOW);
                    } else if (diff == -1) {
                        if (val.x - 1 == x) {
                            P p1 = new P(blank.x + 1, blank.y);
                            P p2 = new P(p1.x, p1.y + 1);
                            swap(blank, p1); swap(p1, p2);
                            continue;
                        }
                        perform(blank, val, Pattern.SHUFFLE_UP_ABOVE);
                    }
                } else if (val.y == blank.y) {
                    int diff = blank.x - val.x;
                    if (diff == 1) {
                        if (y == D - 1) {
                            perform(blank, val, Pattern.LAST_PIECE_COLUMN);
                            return;
                        }
                        perform(blank, val, Pattern.ROTATE_LEFT_DOWN);
                    } else if (diff == -1) {
                        swap(blank, val);
                    }
                }
            }
            throw new IllegalStateException("swapLeftColumn stalled");
        }

        void perform(P blank, P val, Pattern pattern) {
            int[] offsets;
            switch (pattern) {
                case ROTATE_LEFT_UP:
                case ROTATE_RIGHT_UP:
                case ROTATE_RIGHT_DOWN:
                case ROTATE_LEFT_DOWN:
                    offsets = Pattern.ROTATE_LEFT_UP.points;
                    break;
                case ROTATE_UP_LEFT:
                case ROTATE_UP_RIGHT:
                case ROTATE_DOWN_LEFT:
                case ROTATE_DOWN_RIGHT:
                    offsets = Pattern.ROTATE_UP_LEFT.points;
                    break;
                default:
                    offsets = pattern.points;
            }

            ArrayList<P> points = new ArrayList<>();
            for (int i = 0; i < offsets.length; i += 2) {
                points.add(new P(
                    val.x + pattern.modX * offsets[i],
                    val.y + pattern.modY * offsets[i + 1]
                ));
            }
            points.add(val);

            if (pattern != Pattern.LAST_PIECE_ROW && pattern != Pattern.LAST_PIECE_COLUMN) {
                P start = blank;
                for (P point : points) {
                    swap(start, point);
                    start = point;
                }
            } else {
                P p1 = points.get(0), p2 = points.get(1), p3 = points.get(2), p4 = points.get(3);
                swap(blank, val);
                swap(val, p3);
                swap(p3, p1);
                swap(p1, p2);
                swap(p2, val);
                swap(val, p3);
                swap(p3, p1);
                swap(p1, p2);
                swap(p2, val);
                swap(val, blank);
                swap(blank, p4);
                swap(p4, p3);
                swap(p3, p1);
                swap(p1, p2);
                swap(p2, val);
            }
        }

        P find(int value) {
            for (int y = 0; y < D; y++) {
                for (int x = 0; x < D; x++) {
                    if (pieceAt(x, y) == value) return new P(x, y);
                }
            }
            throw new IllegalStateException("piece not found");
        }

        int pieceAt(int x, int y) {
            if (x < 0 || y < 0 || x >= D || y >= D) {
                throw new IllegalStateException("move outside puzzle");
            }
            return board[y * D + x];
        }

        void swap(P a, P b) {
            if (++swaps > MAX_PRE_SWAPS) throw new IllegalStateException("too many presolve swaps");
            int ia = a.y * D + a.x;
            int ib = b.y * D + b.x;
            boolean aGap = board[ia] == GAP;
            boolean bGap = board[ib] == GAP;
            if (aGap == bGap || Math.abs(a.x - b.x) + Math.abs(a.y - b.y) != 1) {
                throw new IllegalStateException("invalid puzzle swap");
            }

            int tapped = aGap ? ib : ia;
            int tmp = board[ia];
            board[ia] = board[ib];
            board[ib] = tmp;
            moves.add(tapped);
        }
    }

    private static List<Integer> idaSolve(int[] start) {
        int[] board = start.clone();
        if (isGoal(board)) return new ArrayList<>();

        int gap = findGap(board);
        int bound = manhattan(board);
        ArrayList<Integer> path = new ArrayList<>();

        while (bound <= MAX_IDA_BOUND) {
            SearchResult result = search(board, gap, -1, 0, bound, path);
            if (result.found) return new ArrayList<>(path);
            if (result.nextBound == Integer.MAX_VALUE) return null;
            bound = result.nextBound;
        }
        return null;
    }

    private static final class SearchResult {
        final boolean found;
        final int nextBound;
        SearchResult(boolean found, int nextBound) {
            this.found = found;
            this.nextBound = nextBound;
        }
    }

    private static SearchResult search(
        int[] board,
        int gap,
        int previousGap,
        int depth,
        int bound,
        ArrayList<Integer> path
    ) {
        int h = manhattan(board);
        int f = depth + h;
        if (f > bound) return new SearchResult(false, f);
        if (h == 0) return new SearchResult(true, f);

        int min = Integer.MAX_VALUE;
        int row = gap / D;
        int col = gap % D;
        int[] candidates = new int[]{
            row > 0 ? gap - D : -1,
            row + 1 < D ? gap + D : -1,
            col > 0 ? gap - 1 : -1,
            col + 1 < D ? gap + 1 : -1
        };

        for (int next : candidates) {
            if (next < 0 || next == previousGap) continue;

            board[gap] = board[next];
            board[next] = GAP;
            path.add(next);

            SearchResult result = search(board, next, gap, depth + 1, bound, path);
            if (result.found) return result;
            if (result.nextBound < min) min = result.nextBound;

            path.remove(path.size() - 1);
            board[next] = board[gap];
            board[gap] = GAP;
        }

        return new SearchResult(false, min);
    }

    private static int manhattan(int[] board) {
        int value = 0;
        for (int pos = 0; pos < N; pos++) {
            int piece = board[pos];
            if (piece == GAP) continue;
            value += Math.abs(pos / D - piece / D) + Math.abs(pos % D - piece % D);
        }
        return value;
    }

    private static int findGap(int[] board) {
        for (int i = 0; i < board.length; i++) if (board[i] == GAP) return i;
        return -1;
    }

    private static boolean isGoal(int[] board) {
        for (int i = 0; i < N; i++) if (board[i] != i) return false;
        return true;
    }
}
