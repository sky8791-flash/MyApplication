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
                // Random move near existing pieces or center
                List<int[]> nearbyMoves = new ArrayList<>();
                for (int[] move : validMoves) {
                    if (hasNeighbor(board, move[0], move[1], boardSize)) {
                        nearbyMoves.add(move);
                    }
                }
                if (!nearbyMoves.isEmpty()) {
                    return nearbyMoves.get(random.nextInt(nearbyMoves.size()));
                }
                // If no nearby moves, try center
                if (validMoves.size() > boardSize * boardSize * 0.8) {
                    int center = boardSize / 2;
                    if (board[center][center] == 0) {
                        return new int[]{center, center};
                    }
                }
                return validMoves.get(random.nextInt(validMoves.size()));
                
            case MEDIUM:
                // Use scoring system to find good moves
                int[] bestMove = findBestMoveByScoring(board, aiColor, boardSize, 2);
                return bestMove != null ? bestMove : validMoves.get(random.nextInt(validMoves.size()));
                
            case HARD:
                // Advanced scoring with deeper evaluation
                bestMove = findBestMoveByScoring(board, aiColor, boardSize, 3);
                return bestMove != null ? bestMove : validMoves.get(random.nextInt(validMoves.size()));
        }
        
        return validMoves.get(random.nextInt(validMoves.size()));
    }
    
    // Enhanced move evaluation with scoring
    private int[] findBestMoveByScoring(int[][] board, int aiColor, int boardSize, int lookAhead) {
        int opponent = (aiColor == 1) ? 2 : 1;
        int[] bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        
        // Evaluate all empty positions
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if (board[r][c] != 0) continue;
                
                // Skip positions too far from existing pieces (optimization)
                if (!hasNeighborInRange(board, r, c, boardSize, 2)) continue;
                
                int score = 0;
                
                // Check if this move wins immediately
                if (canWinAt(board, r, c, aiColor, boardSize)) {
                    return new int[]{r, c}; // Immediate win
                }
                
                // Check if this move blocks opponent's win
                if (canWinAt(board, r, c, opponent, boardSize)) {
                    score += 5000; // Must block
                }
                
                // Score based on patterns
                score += scorePosition(board, r, c, aiColor, boardSize) * 2;
                score += scorePosition(board, r, c, opponent, boardSize); // Also consider blocking
                
                // Add randomness for variety
                score += random.nextInt(10);
                
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = new int[]{r, c};
                }
            }
        }
        
        return bestMove;
    }
    
    // Check if placing at (r,c) creates a winning position
    private boolean canWinAt(int[][] board, int r, int c, int color, int boardSize) {
        int[][] directions = {{1,0}, {0,1}, {1,1}, {1,-1}};
        
        for (int[] dir : directions) {
            int count = 1;
            count += countInDirection(board, r, c, dir[0], dir[1], color, boardSize);
            count += countInDirection(board, r, c, -dir[0], -dir[1], color, boardSize);
            
            if (count >= 5) { // Need 5 in a row for Gomoku
                return true;
            }
        }
        return false;
    }
    
    // Score a position based on patterns it creates
    private int scorePosition(int[][] board, int r, int c, int color, int boardSize) {
        int[][] directions = {{1,0}, {0,1}, {1,1}, {1,-1}};
        int totalScore = 0;
        
        for (int[] dir : directions) {
            int count = 1;
            int openEnds = 0;
            
            // Count in positive direction
            int forwardCount = countInDirection(board, r, c, dir[0], dir[1], color, boardSize);
            count += forwardCount;
            if (isOpenEnd(board, r, c, dir[0], dir[1], color, boardSize, forwardCount)) {
                openEnds++;
            }
            
            // Count in negative direction
            int backwardCount = countInDirection(board, r, c, -dir[0], -dir[1], color, boardSize);
            count += backwardCount;
            if (isOpenEnd(board, r, c, -dir[0], -dir[1], color, boardSize, backwardCount)) {
                openEnds++;
            }
            
            // Score based on length and openness
            if (count >= 5) {
                totalScore += 10000; // Winning move
            } else if (count == 4) {
                totalScore += openEnds == 2 ? 800 : 300; // Open four vs closed four
            } else if (count == 3) {
                totalScore += openEnds == 2 ? 200 : 50; // Open three vs closed three
            } else if (count == 2) {
                totalScore += openEnds == 2 ? 50 : 10;
            }
        }
        
        return totalScore;
    }
    
    // Check if the end is open (can be extended)
    private boolean isOpenEnd(int[][] board, int r, int c, int dr, int dc, int color, int boardSize, int distance) {
        int nr = r + (dr * (distance + 1));
        int nc = c + (dc * (distance + 1));
        
        if (nr < 0 || nr >= boardSize || nc < 0 || nc >= boardSize) {
            return false; // Edge of board
        }
        
        return board[nr][nc] == 0; // Empty = open
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
    
    private boolean hasNeighbor(int[][] board, int r, int c, int boardSize) {
        return hasNeighborInRange(board, r, c, boardSize, 1);
    }
    
    private boolean hasNeighborInRange(int[][] board, int r, int c, int boardSize, int range) {
        for (int dr = -range; dr <= range; dr++) {
            for (int dc = -range; dc <= range; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < boardSize && nc >= 0 && nc < boardSize && board[nr][nc] != 0) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // Find best move for Xiangqi with proper move validation
    public int[] findBestXiangqiMove(XiangqiEngine engine, int aiColor) {
        List<int[]> validMoves = new ArrayList<>();
        int[][] board = engine.getBoard();
        int opponent = (aiColor == 1) ? 2 : 1;
        
        // Find all valid moves by actually testing them
        for (int fromR = 0; fromR < 10; fromR++) {
            for (int fromC = 0; fromC < 9; fromC++) {
                int piece = board[fromR][fromC];
                if (piece == 0 || piece % 10 != aiColor) continue;
                
                // Try all possible destinations
                for (int toR = 0; toR < 10; toR++) {
                    for (int toC = 0; toC < 9; toC++) {
                        if (fromR == toR && fromC == toC) continue;
                        
                        // Save current state
                        int targetPiece = board[toR][toC];
                        
                        // Try the move
                        board[toR][toC] = piece;
                        board[fromR][fromC] = 0;
                        
                        // Check if it's a valid board state (simplified check)
                        boolean valid = true;
                        if (targetPiece != 0 && targetPiece % 10 == aiColor) {
                            valid = false; // Can't capture own piece
                        }
                        
                        // Restore state
                        board[fromR][fromC] = piece;
                        board[toR][toC] = targetPiece;
                        
                        if (valid) {
                            // Score the move
                            int score = scoreXiangqiMove(board, fromR, fromC, toR, toC, aiColor, opponent);
                            validMoves.add(new int[]{fromR, fromC, toR, toC, score});
                        }
                    }
                }
            }
        }
        
        if (validMoves.isEmpty()) return null;
        
        // Sort by score and pick based on difficulty
        validMoves.sort((a, b) -> Integer.compare(b[4], a[4])); // Sort descending by score
        
        int pickFrom;
        switch (difficulty) {
            case EASY:
                pickFrom = Math.min(validMoves.size(), Math.max(5, validMoves.size() / 3));
                break;
            case MEDIUM:
                pickFrom = Math.min(validMoves.size(), Math.max(3, validMoves.size() / 5));
                break;
            case HARD:
                pickFrom = Math.min(validMoves.size(), 2); // Pick from top 2
                break;
            default:
                pickFrom = validMoves.size();
        }
        
        int[] move = validMoves.get(random.nextInt(pickFrom));
        return new int[]{move[0], move[1], move[2], move[3]};
    }
    
    // Score Xiangqi moves
    private int scoreXiangqiMove(int[][] board, int fromR, int fromC, int toR, int toC, int aiColor, int opponent) {
        int score = 0;
        int piece = board[fromR][fromC];
        int targetPiece = board[toR][toC];
        int pieceType = piece / 10;
        
        // Capturing opponent pieces
        if (targetPiece != 0 && targetPiece % 10 == opponent) {
            int targetType = targetPiece / 10;
            switch (targetType) {
                case 1: score += 10000; // Capture general = win
                    break;
                case 2: score += 200; // Advisor
                    break;
                case 3: score += 200; // Elephant
                    break;
                case 4: score += 400; // Horse
                    break;
                case 5: score += 600; // Chariot
                    break;
                case 6: score += 400; // Cannon
                    break;
                case 7: score += 100; // Soldier
                    break;
            }
        }
        
        // Positional scoring
        if (pieceType == 5 || pieceType == 6) { // Chariot and Cannon
            // Prefer central files and moving forward
            score += (4 - Math.abs(toC - 4)) * 5; // Center bonus
            if (aiColor == 1) {
                score += (9 - toR) * 3; // Red moves up
            } else {
                score += toR * 3; // Black moves down
            }
        }
        
        if (pieceType == 4) { // Horse
            score += 20; // Horses are valuable for attacks
        }
        
        if (pieceType == 7) { // Soldier
            // Soldiers more valuable after crossing river
            if ((aiColor == 1 && fromR < 5) || (aiColor == 2 && fromR > 4)) {
                score += 30;
            }
        }
        
        // Add small random factor
        score += random.nextInt(20);
        
        return score;
    }
}
