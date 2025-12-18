package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class GoEngineTest {
    
    @Test
    public void testPlaceStone() {
        GoEngine engine = new GoEngine(19);
        assertTrue(engine.place(3, 3, 1)); // Black places stone
        assertEquals(1, engine.getBoard()[3][3]);
    }
    
    @Test
    public void testCannotPlaceOnOccupiedPosition() {
        GoEngine engine = new GoEngine(19);
        assertTrue(engine.place(3, 3, 1));
        assertFalse(engine.place(3, 3, 2)); // Cannot place on occupied position
    }
    
    @Test
    public void testCapture() {
        GoEngine engine = new GoEngine(19);
        // Create a situation where a white stone is surrounded
        engine.place(1, 0, 2); // White
        engine.place(0, 0, 1); // Black surrounds from top
        engine.place(2, 0, 1); // Black surrounds from bottom
        engine.place(1, 1, 1); // Black surrounds from right
        
        // The white stone should be captured and position should be empty
        assertEquals(0, engine.getBoard()[1][0]);
    }
    
    @Test
    public void testSuicideNotAllowed() {
        GoEngine engine = new GoEngine(19);
        // Create a suicide situation
        engine.place(1, 0, 2);
        engine.place(0, 1, 2);
        
        // Try to place a stone that would have no liberties (suicide)
        assertFalse(engine.place(0, 0, 1));
    }
    
    @Test
    public void testUndo() {
        GoEngine engine = new GoEngine(19);
        engine.place(3, 3, 1);
        engine.place(4, 4, 2);
        
        assertTrue(engine.undo(1));
        assertEquals(0, engine.getBoard()[4][4]);
        assertEquals(1, engine.getBoard()[3][3]); // First stone still there
    }
    
    @Test
    public void testLastMove() {
        GoEngine engine = new GoEngine(19);
        engine.place(3, 3, 1);
        
        GoEngine.Move lastMove = engine.getLastMove();
        assertNotNull(lastMove);
        assertEquals(3, lastMove.r);
        assertEquals(3, lastMove.c);
        assertEquals(1, lastMove.color);
    }
}
