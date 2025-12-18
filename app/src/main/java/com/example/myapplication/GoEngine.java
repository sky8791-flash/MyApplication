package com.example.myapplication;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class GoEngine {
    public static class Move {
        public final int r, c, color; // color: 1=黑, 2=白
        public Move(int r, int c, int color) { this.r = r; this.c = c; this.color = color; }
    }

    private final int size;
    private final int[][] board;
    private final Deque<Move> stack = new ArrayDeque<>();
    private final Deque<int[][]> boardHistory = new ArrayDeque<>(); // For ko rule tracking

    public GoEngine(int size) {
        this.size = size;
        this.board = new int[size][size];
    }

    public boolean place(int r, int c, int color) {
        if (r < 0 || c < 0 || r >= size || c >= size) return false;
        if (board[r][c] != 0) return false;

        // Save current state for ko rule check
        int[][] beforeState = copyBoard(board);
        
        board[r][c] = color;
        
        // Remove captured opponent stones
        int opponent = other(color);
        boolean captured = false;
        int[][] dirs = {{0,1},{1,0},{0,-1},{-1,0}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == opponent) {
                if (!hasLiberty(nr, nc)) {
                    removeGroup(nr, nc);
                    captured = true;
                }
            }
        }
        
        // Check if own stone has liberty (suicide rule)
        if (!hasLiberty(r, c)) {
            // Restore board
            copyBoard(beforeState, board);
            return false;
        }
        
        // Ko rule: check if board state matches the immediate previous state
        if (!boardHistory.isEmpty() && boardsEqual(board, boardHistory.peekLast())) {
            copyBoard(beforeState, board);
            return false;
        }
        
        // Save state for ko rule and move history
        boardHistory.addLast(beforeState);
        if (boardHistory.size() > 2) {
            boardHistory.removeFirst(); // Keep only last 2 states for simple ko
        }
        
        stack.addLast(new Move(r, c, color));
        return true;
    }

    private boolean hasLiberty(int r, int c) {
        int color = board[r][c];
        if (color == 0) return true;
        
        boolean[][] visited = new boolean[size][size];
        return hasLibertyDFS(r, c, color, visited);
    }

    private boolean hasLibertyDFS(int r, int c, int color, boolean[][] visited) {
        if (r < 0 || r >= size || c < 0 || c >= size) return false;
        if (visited[r][c]) return false;
        if (board[r][c] == 0) return true; // Found liberty
        if (board[r][c] != color) return false; // Different color
        
        visited[r][c] = true;
        int[][] dirs = {{0,1},{1,0},{0,-1},{-1,0}};
        for (int[] d : dirs) {
            if (hasLibertyDFS(r + d[0], c + d[1], color, visited)) {
                return true;
            }
        }
        return false;
    }

    private void removeGroup(int r, int c) {
        int color = board[r][c];
        if (color == 0) return;
        
        board[r][c] = 0;
        int[][] dirs = {{0,1},{1,0},{0,-1},{-1,0}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == color) {
                removeGroup(nr, nc);
            }
        }
    }

    private int[][] copyBoard(int[][] src) {
        int[][] dst = new int[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, size);
        }
        return dst;
    }

    private void copyBoard(int[][] src, int[][] dst) {
        for (int i = 0; i < size; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, size);
        }
    }

    private boolean boardsEqual(int[][] b1, int[][] b2) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (b1[i][j] != b2[i][j]) return false;
            }
        }
        return true;
    }

    public boolean undo(int steps) {
        boolean ok = false;
        for (int i = 0; i < steps; i++) {
            Move m = stack.pollLast();
            if (m == null) break;
            
            // Restore previous board state if available
            if (!boardHistory.isEmpty()) {
                int[][] prevState = boardHistory.pollLast();
                copyBoard(prevState, board);
            } else {
                // Fallback: just clear the stone if no history
                board[m.r][m.c] = 0;
            }
            ok = true;
        }
        return ok;
    }

    public int[][] getBoard() { return board; }
    public Move getLastMove() { return stack.peekLast(); }

    private int other(int c) { return c == 1 ? 2 : 1; }

    // For Go, there's no simple "win" condition - game ends by agreement or passing
    // This is a placeholder that always returns false
    public boolean checkWin(int r, int c) {
        return false; // Go has no immediate win condition
    }
}
