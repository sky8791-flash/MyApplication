package com.example.myapplication;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.example.myapplication.GomokuEngine;

public class BoardView extends View {
    public interface OnPlaceListener { void onPlace(int r, int c); }
    public interface OnXiangqiMoveListener { void onMove(int fromR, int fromC, int toR, int toC); }

    public enum GameType { GOMOKU, GO, XIANGQI }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private GomokuEngine gomokuEngine;
    private GoEngine goEngine;
    private XiangqiEngine xiangqiEngine;
    private OnPlaceListener placeListener;
    private OnXiangqiMoveListener xiangqiMoveListener;
    private int size = 15;
    private GameType gameType = GameType.GOMOKU;
    private int selectedR = -1, selectedC = -1; // For Xiangqi piece selection

    public BoardView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() {
        linePaint.setColor(Color.DKGRAY);
        linePaint.setStrokeWidth(3f);
        blackPaint.setColor(Color.BLACK);
        whitePaint.setColor(Color.WHITE);
        whitePaint.setShadowLayer(6f, 2f, 2f, Color.GRAY);
        redPaint.setColor(Color.RED);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_SOFTWARE, whitePaint);
    }

    public void bindGomokuEngine(GomokuEngine e) { 
        this.gomokuEngine = e; 
        this.size = e.getBoard().length; 
        this.gameType = GameType.GOMOKU;
        invalidate(); 
    }
    
    public void bindGoEngine(GoEngine e) { 
        this.goEngine = e; 
        this.size = e.getBoard().length; 
        this.gameType = GameType.GO;
        invalidate(); 
    }
    
    public void bindXiangqiEngine(XiangqiEngine e) { 
        this.xiangqiEngine = e; 
        this.gameType = GameType.XIANGQI;
        invalidate(); 
    }
    
    public void setOnPlaceListener(OnPlaceListener l) { this.placeListener = l; }
    public void setOnXiangqiMoveListener(OnXiangqiMoveListener l) { this.xiangqiMoveListener = l; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (gameType == GameType.GOMOKU || gameType == GameType.GO) {
            drawGoOrGomoku(canvas);
        } else if (gameType == GameType.XIANGQI) {
            drawXiangqi(canvas);
        }
    }

    private void drawGoOrGomoku(Canvas canvas) {
        if ((gameType == GameType.GOMOKU && gomokuEngine == null) || 
            (gameType == GameType.GO && goEngine == null)) return;
            
        float w = getWidth(), h = getHeight();
        float cellX = w / (size - 1), cellY = h / (size - 1);

        // grid
        for (int i = 0; i < size; i++) {
            canvas.drawLine(0, i * cellY, w, i * cellY, linePaint);
            canvas.drawLine(i * cellX, 0, i * cellX, h, linePaint);
        }
        
        // stones
        int[][] board = (gameType == GameType.GOMOKU) ? gomokuEngine.getBoard() : goEngine.getBoard();
        float rStone = Math.min(cellX, cellY) * 0.4f;
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
            if (board[r][c] == 0) continue;
            float cx = c * cellX; float cy = r * cellY;
            canvas.drawCircle(cx, cy, rStone, board[r][c] == 1 ? blackPaint : whitePaint);
        }
        
        // last move marker
        Object lastMove = (gameType == GameType.GOMOKU) ? gomokuEngine.getLastMove() : goEngine.getLastMove();
        if (lastMove != null) {
            int lastR = 0, lastC = 0;
            if (gameType == GameType.GOMOKU) {
                GomokuEngine.Move m = (GomokuEngine.Move) lastMove;
                lastR = m.r; lastC = m.c;
            } else {
                GoEngine.Move m = (GoEngine.Move) lastMove;
                lastR = m.r; lastC = m.c;
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.RED); p.setStrokeWidth(6f); p.setStyle(Paint.Style.STROKE);
            float cx = lastC * cellX, cy = lastR * cellY;
            canvas.drawCircle(cx, cy, rStone * 0.5f, p);
        }
    }

    private void drawXiangqi(Canvas canvas) {
        if (xiangqiEngine == null) return;
        
        float w = getWidth(), h = getHeight();
        float cellX = w / 8f;
        float cellY = h / 9f;
        
        // Draw board lines
        for (int r = 0; r < 10; r++) {
            canvas.drawLine(0, r * cellY, 8 * cellX, r * cellY, linePaint);
        }
        for (int c = 0; c < 9; c++) {
            // Skip river in middle columns
            if (c == 0 || c == 8) {
                canvas.drawLine(c * cellX, 0, c * cellX, 9 * cellY, linePaint);
            } else {
                canvas.drawLine(c * cellX, 0, c * cellX, 4 * cellY, linePaint);
                canvas.drawLine(c * cellX, 5 * cellY, c * cellX, 9 * cellY, linePaint);
            }
        }
        
        // Draw palace diagonals
        canvas.drawLine(3 * cellX, 0, 5 * cellX, 2 * cellY, linePaint);
        canvas.drawLine(5 * cellX, 0, 3 * cellX, 2 * cellY, linePaint);
        canvas.drawLine(3 * cellX, 7 * cellY, 5 * cellX, 9 * cellY, linePaint);
        canvas.drawLine(5 * cellX, 7 * cellY, 3 * cellX, 9 * cellY, linePaint);
        
        // Draw pieces
        int[][] board = xiangqiEngine.getBoard();
        float radius = Math.min(cellX, cellY) * 0.4f;
        String[] pieceNames = {"", "将", "士", "象", "马", "车", "炮", "兵"};
        String[] pieceNamesBlack = {"", "将", "士", "象", "马", "车", "炮", "卒"};
        
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                
                float cx = c * cellX;
                float cy = r * cellY;
                
                int pieceType = piece / 10;
                int color = piece % 10;
                
                // Draw circle
                canvas.drawCircle(cx, cy, radius, whitePaint);
                Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                circlePaint.setStyle(Paint.Style.STROKE);
                circlePaint.setStrokeWidth(3f);
                circlePaint.setColor(color == 1 ? Color.RED : Color.BLACK);
                canvas.drawCircle(cx, cy, radius, circlePaint);
                
                // Draw text
                Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
                textP.setColor(color == 1 ? Color.RED : Color.BLACK);
                textP.setTextSize(radius * 1.2f);
                textP.setTextAlign(Paint.Align.CENTER);
                String text = (color == 2) ? pieceNamesBlack[pieceType] : pieceNames[pieceType];
                canvas.drawText(text, cx, cy + radius * 0.4f, textP);
            }
        }
        
        // Highlight selected piece
        if (selectedR >= 0 && selectedC >= 0) {
            float cx = selectedC * cellX;
            float cy = selectedR * cellY;
            Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            highlightPaint.setColor(Color.YELLOW);
            highlightPaint.setStyle(Paint.Style.STROKE);
            highlightPaint.setStrokeWidth(6f);
            canvas.drawCircle(cx, cy, radius * 1.1f, highlightPaint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (gameType == GameType.XIANGQI) {
                return handleXiangqiTouch(e);
            } else {
                return handleGoGomokuTouch(e);
            }
        }
        return true;
    }

    private boolean handleGoGomokuTouch(MotionEvent e) {
        if ((gameType == GameType.GOMOKU && gomokuEngine == null) || 
            (gameType == GameType.GO && goEngine == null) || placeListener == null) return false;
            
        float w = getWidth(), h = getHeight();
        float cellX = w / (size - 1), cellY = h / (size - 1);
        int c = Math.round(e.getX() / cellX);
        int r = Math.round(e.getY() / cellY);
        placeListener.onPlace(r, c);
        return true;
    }

    private boolean handleXiangqiTouch(MotionEvent e) {
        if (xiangqiEngine == null || xiangqiMoveListener == null) return false;
        
        float w = getWidth(), h = getHeight();
        float cellX = w / 8f;
        float cellY = h / 9f;
        int c = Math.round(e.getX() / cellX);
        int r = Math.round(e.getY() / cellY);
        
        if (r < 0 || r >= 10 || c < 0 || c >= 9) return false;
        
        if (selectedR == -1) {
            // Select piece
            if (xiangqiEngine.getBoard()[r][c] != 0) {
                selectedR = r;
                selectedC = c;
                invalidate();
            }
        } else {
            // Move piece
            xiangqiMoveListener.onMove(selectedR, selectedC, r, c);
            selectedR = -1;
            selectedC = -1;
            invalidate();
        }
        return true;
    }
}