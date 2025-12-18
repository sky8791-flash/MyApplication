package com.example.myapplication;

import java.util.ArrayDeque;
import java.util.Deque;

public class GomokuEngine {
    public static class Move {
        public final int r, c, color; // color: 1=黑, 2=白
        public Move(int r, int c, int color) { this.r = r; this.c = c; this.color = color; }
    }

    private final int size;
    private final int[][] board;
    private final Deque<Move> stack = new ArrayDeque<>();

    public GomokuEngine(int size) {
        this.size = size;
        this.board = new int[size][size];
    }

    public boolean place(int r, int c, int color) {
        if (r < 0 || c < 0 || r >= size || c >= size) return false;
        if (board[r][c] != 0) return false;
        board[r][c] = color;
        stack.addLast(new Move(r, c, color));
        return true;
    }

    public boolean undo(int steps) {
        boolean ok = false;
        for (int i = 0; i < steps; i++) {
            Move m = stack.pollLast();
            if (m == null) break;
            board[m.r][m.c] = 0;
            ok = true;
        }
        return ok;
    }

    public int[][] getBoard() { return board; }
    public Move getLastMove() { return stack.peekLast(); }

    public boolean checkWin(int r, int c) {
        int color = board[r][c];
        if (color == 0) return false;
        int[][] dirs = {{1,0},{0,1},{1,1},{1,-1}};
        for (int[] d : dirs) {
            int cnt = 1;
            cnt += countDir(r, c, d[0], d[1], color);
            cnt += countDir(r, c, -d[0], -d[1], color);
            if (cnt >= 5) return true;
        }
        return false;
    }

    private int countDir(int r, int c, int dr, int dc, int color) {
        int cnt = 0; int nr = r + dr; int nc = c + dc;
        while (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == color) {
            cnt++; nr += dr; nc += dc;
        }
        return cnt;
    }
}