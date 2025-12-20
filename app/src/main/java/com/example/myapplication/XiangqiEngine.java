package com.example.myapplication;

import java.util.ArrayDeque;
import java.util.Deque;

public class XiangqiEngine {
    public static class Move {
        public final int r, c, piece, color;
        public final int toR, toC, capturedPieceValue;
        public Move(int r, int c, int piece, int color, int toR, int toC, int capturedPieceValue) {
            this.r = r; this.c = c; this.piece = piece; this.color = color;
            this.toR = toR; this.toC = toC; this.capturedPieceValue = capturedPieceValue;
        }
    }

    // Piece types: 1=将/帅, 2=士/仕, 3=象/相, 4=马/馬, 5=车/車, 6=炮/砲, 7=兵/卒
    // Color: 1=红(Red), 2=黑(Black)
    // Board representation: piece * 10 + color (e.g., 51 = red chariot, 52 = black chariot)
    
    private final int[][] board = new int[10][9]; // 10 rows x 9 columns
    private final Deque<Move> stack = new ArrayDeque<>();

    public XiangqiEngine() {
        initBoard();
    }

    private void initBoard() {
        // Red pieces (bottom, color=1)
        board[9][0] = 51; board[9][8] = 51; // 车 Chariot
        board[9][1] = 41; board[9][7] = 41; // 马 Horse
        board[9][2] = 31; board[9][6] = 31; // 象 Elephant
        board[9][3] = 21; board[9][5] = 21; // 士 Advisor
        board[9][4] = 11; // 帅 General
        board[7][1] = 61; board[7][7] = 61; // 炮 Cannon
        board[6][0] = 71; board[6][2] = 71; board[6][4] = 71; board[6][6] = 71; board[6][8] = 71; // 兵 Soldier

        // Black pieces (top, color=2)
        board[0][0] = 52; board[0][8] = 52; // 车 Chariot
        board[0][1] = 42; board[0][7] = 42; // 马 Horse
        board[0][2] = 32; board[0][6] = 32; // 象 Elephant
        board[0][3] = 22; board[0][5] = 22; // 士 Advisor
        board[0][4] = 12; // 将 General
        board[2][1] = 62; board[2][7] = 62; // 炮 Cannon
        board[3][0] = 72; board[3][2] = 72; board[3][4] = 72; board[3][6] = 72; board[3][8] = 72; // 卒 Soldier
    }

    public boolean place(int fromR, int fromC, int toR, int toC, int color) {
        if (fromR < 0 || fromR >= 10 || fromC < 0 || fromC >= 9) return false;
        if (toR < 0 || toR >= 10 || toC < 0 || toC >= 9) return false;
        
        int piece = board[fromR][fromC];
        if (piece == 0) return false;
        if (piece % 10 != color) return false; // Not your piece
        
        int targetPiece = board[toR][toC];
        if (targetPiece != 0 && targetPiece % 10 == color) return false; // Can't capture own piece
        
        if (!isValidMove(fromR, fromC, toR, toC, piece)) return false;
        
        // Make move
        board[toR][toC] = piece;
        board[fromR][fromC] = 0;
        
        stack.addLast(new Move(fromR, fromC, piece / 10, color, toR, toC, targetPiece));
        return true;
    }

    private boolean isValidMove(int fromR, int fromC, int toR, int toC, int piece) {
        int pieceType = piece / 10;
        int color = piece % 10;
        
        switch (pieceType) {
            case 1: return isValidGeneralMove(fromR, fromC, toR, toC, color);
            case 2: return isValidAdvisorMove(fromR, fromC, toR, toC, color);
            case 3: return isValidElephantMove(fromR, fromC, toR, toC, color);
            case 4: return isValidHorseMove(fromR, fromC, toR, toC);
            case 5: return isValidChariotMove(fromR, fromC, toR, toC);
            case 6: return isValidCannonMove(fromR, fromC, toR, toC);
            case 7: return isValidSoldierMove(fromR, fromC, toR, toC, color);
        }
        return false;
    }

    private boolean isValidGeneralMove(int fromR, int fromC, int toR, int toC, int color) {
        // General moves within palace (3x3)
        int minR = (color == 1) ? 7 : 0;
        int maxR = (color == 1) ? 9 : 2;
        if (toR < minR || toR > maxR || toC < 3 || toC > 5) return false;
        
        int dr = Math.abs(toR - fromR);
        int dc = Math.abs(toC - fromC);
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1);
    }

    private boolean isValidAdvisorMove(int fromR, int fromC, int toR, int toC, int color) {
        // Advisor moves diagonally within palace
        int minR = (color == 1) ? 7 : 0;
        int maxR = (color == 1) ? 9 : 2;
        if (toR < minR || toR > maxR || toC < 3 || toC > 5) return false;
        
        return Math.abs(toR - fromR) == 1 && Math.abs(toC - fromC) == 1;
    }

    private boolean isValidElephantMove(int fromR, int fromC, int toR, int toC, int color) {
        // Elephant moves 2 steps diagonally, cannot cross river
        if (color == 1 && toR < 5) return false; // Red elephant can't cross river
        if (color == 2 && toR > 4) return false; // Black elephant can't cross river
        
        int dr = toR - fromR;
        int dc = toC - fromC;
        if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
        
        // Check blocking piece
        int blockR = fromR + dr / 2;
        int blockC = fromC + dc / 2;
        return board[blockR][blockC] == 0;
    }

    private boolean isValidHorseMove(int fromR, int fromC, int toR, int toC) {
        // Horse moves in L-shape
        int dr = Math.abs(toR - fromR);
        int dc = Math.abs(toC - fromC);
        if (!((dr == 2 && dc == 1) || (dr == 1 && dc == 2))) return false;
        
        // Check blocking piece
        int blockR = fromR;
        int blockC = fromC;
        if (dr == 2) blockR += (toR - fromR) / 2;
        else blockC += (toC - fromC) / 2;
        
        return board[blockR][blockC] == 0;
    }

    private boolean isValidChariotMove(int fromR, int fromC, int toR, int toC) {
        // Chariot moves in straight lines
        if (fromR != toR && fromC != toC) return false;
        
        // Check path is clear
        if (fromR == toR) {
            int start = Math.min(fromC, toC) + 1;
            int end = Math.max(fromC, toC);
            for (int c = start; c < end; c++) {
                if (board[fromR][c] != 0) return false;
            }
        } else {
            int start = Math.min(fromR, toR) + 1;
            int end = Math.max(fromR, toR);
            for (int r = start; r < end; r++) {
                if (board[r][fromC] != 0) return false;
            }
        }
        return true;
    }

    private boolean isValidCannonMove(int fromR, int fromC, int toR, int toC) {
        // Cannon moves like chariot but captures by jumping over one piece
        if (fromR != toR && fromC != toC) return false;
        
        int jumpCount = 0;
        if (fromR == toR) {
            int start = Math.min(fromC, toC) + 1;
            int end = Math.max(fromC, toC);
            for (int c = start; c < end; c++) {
                if (board[fromR][c] != 0) jumpCount++;
            }
        } else {
            int start = Math.min(fromR, toR) + 1;
            int end = Math.max(fromR, toR);
            for (int r = start; r < end; r++) {
                if (board[r][fromC] != 0) jumpCount++;
            }
        }
        
        if (board[toR][toC] == 0) return jumpCount == 0; // Move without capture
        else return jumpCount == 1; // Capture with one jump
    }

    private boolean isValidSoldierMove(int fromR, int fromC, int toR, int toC, int color) {
        // Soldier moves forward, and sideways after crossing river
        int dr = toR - fromR;
        int dc = toC - fromC;
        
        if (color == 1) { // Red soldier moves upward
            if (dr == -1 && dc == 0) return true; // Forward
            if (fromR < 5 && dr == 0 && Math.abs(dc) == 1) return true; // Sideways after river
        } else { // Black soldier moves downward
            if (dr == 1 && dc == 0) return true; // Forward
            if (fromR > 4 && dr == 0 && Math.abs(dc) == 1) return true; // Sideways after river
        }
        return false;
    }

    public boolean undo(int steps) {
        boolean ok = false;
        for (int i = 0; i < steps; i++) {
            Move m = stack.pollLast();
            if (m == null) break;
            board[m.r][m.c] = m.piece * 10 + m.color;
            board[m.toR][m.toC] = m.capturedPieceValue;
            ok = true;
        }
        return ok;
    }

    public int[][] getBoard() { return board; }
    public Move getLastMove() { return stack.peekLast(); }
    
    // Test if a move is valid without making it
    public boolean isValidMoveTest(int fromR, int fromC, int toR, int toC, int color) {
        if (fromR < 0 || fromR >= 10 || fromC < 0 || fromC >= 9) return false;
        if (toR < 0 || toR >= 10 || toC < 0 || toC >= 9) return false;
        
        int piece = board[fromR][fromC];
        if (piece == 0) return false;
        if (piece % 10 != color) return false;
        
        int targetPiece = board[toR][toC];
        if (targetPiece != 0 && targetPiece % 10 == color) return false;
        
        return isValidMove(fromR, fromC, toR, toC, piece);
    }

    public boolean checkWin(int toR, int toC) {
        // Check if a general was captured
        boolean redGeneral = false, blackGeneral = false;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] == 11) redGeneral = true;
                if (board[r][c] == 12) blackGeneral = true;
            }
        }
        return !redGeneral || !blackGeneral;
    }
}
