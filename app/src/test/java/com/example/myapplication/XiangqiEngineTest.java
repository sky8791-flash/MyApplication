package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class XiangqiEngineTest {
    
    @Test
    public void testInitialBoard() {
        XiangqiEngine engine = new XiangqiEngine();
        int[][] board = engine.getBoard();
        
        // Check red general at start position
        assertEquals(11, board[9][4]);
        
        // Check black general at start position
        assertEquals(12, board[0][4]);
        
        // Check red chariots
        assertEquals(51, board[9][0]);
        assertEquals(51, board[9][8]);
        
        // Check black chariots
        assertEquals(52, board[0][0]);
        assertEquals(52, board[0][8]);
    }
    
    @Test
    public void testSoldierMoveForward() {
        XiangqiEngine engine = new XiangqiEngine();
        // Red soldier moves forward (upward on board)
        assertTrue(engine.place(6, 0, 5, 0, 1));
        assertEquals(71, engine.getBoard()[5][0]);
        assertEquals(0, engine.getBoard()[6][0]);
    }
    
    @Test
    public void testChariotStraightMove() {
        XiangqiEngine engine = new XiangqiEngine();
        // Move red chariot forward after clearing path
        engine.place(6, 0, 5, 0, 1); // Move soldier out of way
        assertTrue(engine.place(9, 0, 6, 0, 1)); // Move chariot
        assertEquals(51, engine.getBoard()[6][0]);
    }
    
    @Test
    public void testCannotMoveOpponentPiece() {
        XiangqiEngine engine = new XiangqiEngine();
        // Red player tries to move black piece
        assertFalse(engine.place(3, 0, 4, 0, 1)); // Black soldier, red player
    }
    
    @Test
    public void testGeneralMovement() {
        XiangqiEngine engine = new XiangqiEngine();
        // Clear space for general to move
        engine.place(6, 4, 5, 4, 1); // Move soldier
        engine.place(9, 4, 8, 4, 1); // Move general forward
        
        assertEquals(11, engine.getBoard()[8][4]);
        assertEquals(0, engine.getBoard()[9][4]);
    }
    
    @Test
    public void testGeneralStaysInPalace() {
        XiangqiEngine engine = new XiangqiEngine();
        engine.place(6, 4, 5, 4, 1); // Move soldier
        engine.place(9, 4, 8, 4, 1); // Move general
        
        // Try to move general outside palace (column-wise)
        assertFalse(engine.place(8, 4, 8, 2, 1)); // Outside palace
    }
    
    @Test
    public void testUndo() {
        XiangqiEngine engine = new XiangqiEngine();
        engine.place(6, 0, 5, 0, 1);
        
        assertTrue(engine.undo(1));
        assertEquals(71, engine.getBoard()[6][0]); // Soldier back to original position
        assertEquals(0, engine.getBoard()[5][0]);
    }
    
    @Test
    public void testCapture() {
        XiangqiEngine engine = new XiangqiEngine();
        // Move pieces to create capture scenario
        engine.place(6, 0, 5, 0, 1); // Red soldier
        engine.place(5, 0, 4, 0, 1); // Move forward
        engine.place(4, 0, 3, 0, 1); // Capture black soldier
        
        assertEquals(71, engine.getBoard()[3][0]);
        assertNotEquals(72, engine.getBoard()[3][0]); // Black soldier captured
    }
    
    @Test
    public void testCheckWin() {
        XiangqiEngine engine = new XiangqiEngine();
        
        // Initially no win
        assertFalse(engine.checkWin(0, 0));
        
        // Manually remove a general to test win condition
        engine.getBoard()[9][4] = 0; // Remove red general
        assertTrue(engine.checkWin(0, 0));
    }
}
