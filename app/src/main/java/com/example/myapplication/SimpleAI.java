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
        
        // Find all valid moves (empty positions)
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
                // For easy mode: prioritize moves near existing pieces
                List<int[]> nearbyMoves = new ArrayList<>();
                for (int[] move : validMoves) {
                    if (hasNeighborInRange(board, move[0], move[1], boardSize, 2)) {
                        nearbyMoves.add(move);
                    }
                }
                
                // If we have nearby moves, pick one
                if (!nearbyMoves.isEmpty() && nearbyMoves.size() > 0) {
                    return nearbyMoves.get(random.nextInt(nearbyMoves.size()));
                }
                
                // If no pieces on board yet, try center
                int center = boardSize / 2;
                if (board[center][center] == 0) {
                    return new int[]{center, center};
                }
                
                // Otherwise random from all valid moves
                if (validMoves.size() > 0) {
                    return validMoves.get(random.nextInt(validMoves.size()));
                }
                break;
                
            case MEDIUM:
            case HARD:
                // Use scoring system to find good moves
                int[] bestMove = findBestMoveByScoring(board, aiColor, boardSize, difficulty == Difficulty.HARD ? 3 : 2);
                if (bestMove != null) {
                    return bestMove;
                }
                
                // Fallback: try nearby moves
                List<int[]> fallbackMoves = new ArrayList<>();
                for (int[] move : validMoves) {
                    if (hasNeighborInRange(board, move[0], move[1], boardSize, 2)) {
                        fallbackMoves.add(move);
                    }
                }
                if (!fallbackMoves.isEmpty()) {
                    return fallbackMoves.get(random.nextInt(fallbackMoves.size()));
                }
                
                // Last resort: any valid move
                if (validMoves.size() > 0) {
                    return validMoves.get(random.nextInt(validMoves.size()));
                }
                break;
        }
        
        // Final fallback
        return validMoves.isEmpty() ? null : validMoves.get(random.nextInt(validMoves.size()));
    }
    
    // Enhanced move evaluation with scoring
    private int[] findBestMoveByScoring(int[][] board, int aiColor, int boardSize, int lookAhead) {
        int opponent = (aiColor == 1) ? 2 : 1;
        List<int[]> scoredMoves = new ArrayList<>();
        
        // Evaluate all empty positions near existing pieces
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if (board[r][c] != 0) continue;
                
                // Only consider positions near existing pieces (optimization)
                if (!hasNeighborInRange(board, r, c, boardSize, 3)) continue;
                
                int score = 0;
                
                // Check if this move wins immediately
                if (canWinAt(board, r, c, aiColor, boardSize)) {
                    return new int[]{r, c}; // Immediate win - return immediately
                }
                
                // Check if this move blocks opponent's win
                if (canWinAt(board, r, c, opponent, boardSize)) {
                    score += 5000; // Must block winning move
                }
                
                // Score based on patterns for AI
                score += scorePosition(board, r, c, aiColor, boardSize) * 2;
                
                // Also consider defensive scoring (blocking opponent patterns)
                score += scorePosition(board, r, c, opponent, boardSize);
                
                // Add small randomness for variety
                score += random.nextInt(20);
                
                scoredMoves.add(new int[]{r, c, score});
            }
        }
        
        // If we found no moves near pieces, expand search to entire board
        if (scoredMoves.isEmpty()) {
            for (int r = 0; r < boardSize; r++) {
                for (int c = 0; c < boardSize; c++) {
                    if (board[r][c] == 0) {
                        int score = random.nextInt(50);
                        // Slightly prefer center positions
                        int centerDist = Math.abs(r - boardSize/2) + Math.abs(c - boardSize/2);
                        score += (boardSize - centerDist) * 2;
                        scoredMoves.add(new int[]{r, c, score});
                    }
                }
            }
        }
        
        if (scoredMoves.isEmpty()) return null;
        
        // Sort by score (descending)
        scoredMoves.sort(new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(b[2], a[2]);
            }
        });
        
        // Return best move
        int[] best = scoredMoves.get(0);
        return new int[]{best[0], best[1]};
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
        
        // Find all valid moves by testing them through the engine
        for (int fromR = 0; fromR < 10; fromR++) {
            for (int fromC = 0; fromC < 9; fromC++) {
                int piece = board[fromR][fromC];
                if (piece == 0 || piece % 10 != aiColor) continue;
                
                // Try all possible destinations
                for (int toR = 0; toR < 10; toR++) {
                    for (int toC = 0; toC < 9; toC++) {
                        if (fromR == toR && fromC == toC) continue;
                        
                        // Use engine's validation method
                        if (engine.isValidMoveTest(fromR, fromC, toR, toC, aiColor)) {
                            int score = scoreXiangqiMove(board, fromR, fromC, toR, toC, aiColor, opponent);
                            validMoves.add(new int[]{fromR, fromC, toR, toC, score});
                        }
                    }
                }
            }
        }
        
        if (validMoves.isEmpty()) {
            return null; // No valid moves available
        }
        
        // Sort by score (descending)
        validMoves.sort(new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(b[4], a[4]);
            }
        });
        
        // Pick move based on difficulty level
        int pickFrom;
        switch (difficulty) {
            case EASY:
                // Easy: pick from top 40% of moves
                pickFrom = Math.max(1, (int)(validMoves.size() * 0.4));
                break;
            case MEDIUM:
                // Medium: pick from top 20% of moves
                pickFrom = Math.max(1, (int)(validMoves.size() * 0.2));
                break;
            case HARD:
                // Hard: pick from top 3 moves
                pickFrom = Math.min(3, validMoves.size());
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
