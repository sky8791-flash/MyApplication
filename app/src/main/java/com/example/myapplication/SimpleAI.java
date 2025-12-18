package com.example.myapplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SimpleAI {
    
    public enum Difficulty {
        EASY, MEDIUM, HARD
    }
    
    private final Random random = new Random();
    private final Difficulty difficulty;
    
    public SimpleAI(Difficulty difficulty) {
        this.difficulty = difficulty;
    }
    
    // Find best move for Gomoku/Go
    public int[] findBestMoveForBoard(int[][] board, int aiColor, int boardSize) {
        List<int[]> validMoves = new ArrayList<>();
        
        // Find all valid moves
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if (board[r][c] == 0) {
                    validMoves.add(new int[]{r, c});
                }
            }
        }
        
        if (validMoves.isEmpty()) return null;
        
        switch (difficulty) {
            case EASY:
                // Random move
                return validMoves.get(random.nextInt(validMoves.size()));
                
            case MEDIUM:
                // Try to find a good move (block or attack)
                int[] bestMove = findStrategicMove(board, aiColor, boardSize);
                if (bestMove != null) return bestMove;
                // Random if no strategic move found
                return validMoves.get(random.nextInt(validMoves.size()));
                
            case HARD:
                // More advanced strategy
                bestMove = findStrategicMove(board, aiColor, boardSize);
                if (bestMove != null) return bestMove;
                // Center preference for first moves
                if (validMoves.size() > boardSize * boardSize * 0.8) {
                    int center = boardSize / 2;
                    return new int[]{center, center};
                }
                return validMoves.get(random.nextInt(validMoves.size()));
        }
        
        return validMoves.get(random.nextInt(validMoves.size()));
    }
    
    // Find strategic move (attack/defend)
    private int[] findStrategicMove(int[][] board, int aiColor, int boardSize) {
        int opponent = (aiColor == 1) ? 2 : 1;
        
        // First, check if we can win
        int[] winMove = findWinningMove(board, aiColor, boardSize);
        if (winMove != null) return winMove;
        
        // Then, check if we need to block opponent
        int[] blockMove = findWinningMove(board, opponent, boardSize);
        if (blockMove != null) return blockMove;
        
        // Find good position near existing pieces
        return findNearbyMove(board, boardSize);
    }
    
    private int[] findWinningMove(int[][] board, int color, int boardSize) {
        int[][] directions = {{1,0}, {0,1}, {1,1}, {1,-1}};
        
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if (board[r][c] != 0) continue;
                
                // Try placing here
                for (int[] dir : directions) {
                    int count = 1;
                    count += countInDirection(board, r, c, dir[0], dir[1], color, boardSize);
                    count += countInDirection(board, r, c, -dir[0], -dir[1], color, boardSize);
                    
                    if (count >= 4) { // Can make 4 in a row (or 5)
                        return new int[]{r, c};
                    }
                }
            }
        }
        return null;
    }
    
    private int countInDirection(int[][] board, int r, int c, int dr, int dc, int color, int boardSize) {
        int count = 0;
        int nr = r + dr;
        int nc = c + dc;
        
        while (nr >= 0 && nr < boardSize && nc >= 0 && nc < boardSize && board[nr][nc] == color) {
            count++;
            nr += dr;
            nc += dc;
        }
        return count;
    }
    
    private int[] findNearbyMove(int[][] board, int boardSize) {
        // Find a position near existing pieces
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if (board[r][c] == 0 && hasNeighbor(board, r, c, boardSize)) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }
    
    private boolean hasNeighbor(int[][] board, int r, int c, int boardSize) {
        int[][] dirs = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr >= 0 && nr < boardSize && nc >= 0 && nc < boardSize && board[nr][nc] != 0) {
                return true;
            }
        }
        return false;
    }
    
    // Find best move for Xiangqi
    public int[] findBestXiangqiMove(XiangqiEngine engine, int aiColor) {
        List<int[]> validMoves = new ArrayList<>();
        int[][] board = engine.getBoard();
        
        // Find all valid moves for AI pieces
        for (int fromR = 0; fromR < 10; fromR++) {
            for (int fromC = 0; fromC < 9; fromC++) {
                int piece = board[fromR][fromC];
                if (piece == 0 || piece % 10 != aiColor) continue;
                
                // Try all possible destinations
                for (int toR = 0; toR < 10; toR++) {
                    for (int toC = 0; toC < 9; toC++) {
                        // Test if move is valid by trying it
                        int targetPiece = board[toR][toC];
                        if (targetPiece != 0 && targetPiece % 10 == aiColor) continue;
                        
                        // Simple validation - just collect potential moves
                        if (Math.abs(fromR - toR) + Math.abs(fromC - toC) > 0 && 
                            Math.abs(fromR - toR) + Math.abs(fromC - toC) <= 2) {
                            validMoves.add(new int[]{fromR, fromC, toR, toC});
                        }
                    }
                }
            }
        }
        
        if (validMoves.isEmpty()) return null;
        
        // Return random move based on difficulty
        int[] move = validMoves.get(random.nextInt(Math.min(validMoves.size(), 
            difficulty == Difficulty.EASY ? 3 : difficulty == Difficulty.MEDIUM ? 10 : validMoves.size())));
        
        return move;
    }
}
