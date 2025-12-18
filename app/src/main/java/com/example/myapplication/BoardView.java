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
        linePaint.setColor(Color.parseColor("#8B4513"));
        linePaint.setStrokeWidth(2.5f);
        blackPaint.setColor(Color.BLACK);
        blackPaint.setShadowLayer(4f, 2f, 2f, Color.parseColor("#40000000"));
        whitePaint.setColor(Color.WHITE);
        whitePaint.setShadowLayer(6f, 2f, 2f, Color.GRAY);
        redPaint.setColor(Color.RED);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_SOFTWARE, whitePaint);
        setLayerType(LAYER_TYPE_SOFTWARE, blackPaint);
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
        
        // Draw board background with gradient
        Paint bgPaint = new Paint();
        int bgColor = (gameType == GameType.GO) ? 
            Color.parseColor("#DCB35C") : Color.parseColor("#DEB887");
        bgPaint.setColor(bgColor);
        canvas.drawRect(0, 0, w, h, bgPaint);

        // grid with better styling
        Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#8B4513"));
        gridPaint.setStrokeWidth(2f);
        for (int i = 0; i < size; i++) {
            canvas.drawLine(0, i * cellY, w, i * cellY, gridPaint);
            canvas.drawLine(i * cellX, 0, i * cellX, h, gridPaint);
        }
        
        // Draw star points for Go
        if (gameType == GameType.GO && size == 19) {
            Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            starPaint.setColor(Color.parseColor("#8B4513"));
            float starRadius = 6f;
            int[] starPoints = {3, 9, 15};
            for (int r : starPoints) {
                for (int c : starPoints) {
                    canvas.drawCircle(c * cellX, r * cellY, starRadius, starPaint);
                }
            }
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
        
        // Calculate proper cell size to maintain aspect ratio (10:9)
        // Xiangqi board is 9 columns x 10 rows
        float cellSize = Math.min(w / 8f, h / 9f);
        float boardWidth = cellSize * 8f;
        float boardHeight = cellSize * 9f;
        float offsetX = (w - boardWidth) / 2f;
        float offsetY = (h - boardHeight) / 2f;
        
        float cellX = cellSize;
        float cellY = cellSize;
        
        // Draw board background
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#F4E4C1"));
        canvas.drawRect(0, 0, w, h, bgPaint);
        
        // Draw board lines with better styling
        Paint boardLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardLinePaint.setColor(Color.parseColor("#8B4513"));
        boardLinePaint.setStrokeWidth(2.5f);
        
        for (int r = 0; r < 10; r++) {
            canvas.drawLine(offsetX, offsetY + r * cellY, offsetX + 8 * cellX, offsetY + r * cellY, boardLinePaint);
        }
        for (int c = 0; c < 9; c++) {
            // Skip river in middle columns
            if (c == 0 || c == 8) {
                canvas.drawLine(offsetX + c * cellX, offsetY, offsetX + c * cellX, offsetY + 9 * cellY, boardLinePaint);
            } else {
                canvas.drawLine(offsetX + c * cellX, offsetY, offsetX + c * cellX, offsetY + 4 * cellY, boardLinePaint);
                canvas.drawLine(offsetX + c * cellX, offsetY + 5 * cellY, offsetX + c * cellX, offsetY + 9 * cellY, boardLinePaint);
            }
        }
        
        // Draw river text
        Paint riverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        riverPaint.setColor(Color.parseColor("#40000000"));
        riverPaint.setTextSize(cellY * 0.6f);
        riverPaint.setTextAlign(Paint.Align.CENTER);
        riverPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("楚河", offsetX + boardWidth * 0.3f, offsetY + 4.5f * cellY + riverPaint.getTextSize() * 0.35f, riverPaint);
        canvas.drawText("汉界", offsetX + boardWidth * 0.7f, offsetY + 4.5f * cellY + riverPaint.getTextSize() * 0.35f, riverPaint);
        
        // Draw palace diagonals
        canvas.drawLine(offsetX + 3 * cellX, offsetY, offsetX + 5 * cellX, offsetY + 2 * cellY, boardLinePaint);
        canvas.drawLine(offsetX + 5 * cellX, offsetY, offsetX + 3 * cellX, offsetY + 2 * cellY, boardLinePaint);
        canvas.drawLine(offsetX + 3 * cellX, offsetY + 7 * cellY, offsetX + 5 * cellX, offsetY + 9 * cellY, boardLinePaint);
        canvas.drawLine(offsetX + 5 * cellX, offsetY + 7 * cellY, offsetX + 3 * cellX, offsetY + 9 * cellY, boardLinePaint);
        
        // Draw pieces
        int[][] board = xiangqiEngine.getBoard();
        float radius = Math.min(cellX, cellY) * 0.42f;
        String[] pieceNames = {"", "帅", "仕", "相", "馬", "車", "炮", "兵"};
        String[] pieceNamesBlack = {"", "将", "士", "象", "马", "车", "砲", "卒"};
        
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                
                float cx = offsetX + c * cellX;
                float cy = offsetY + r * cellY;
                
                int pieceType = piece / 10;
                int color = piece % 10;
                
                // Draw piece background with gradient effect
                Paint bgPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                bgPiecePaint.setColor(Color.parseColor("#FFF8DC"));
                bgPiecePaint.setShadowLayer(8f, 3f, 3f, Color.parseColor("#60000000"));
                canvas.drawCircle(cx, cy, radius, bgPiecePaint);
                
                // Draw outer circle
                Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                circlePaint.setStyle(Paint.Style.STROKE);
                circlePaint.setStrokeWidth(3.5f);
                circlePaint.setColor(color == 1 ? Color.parseColor("#D32F2F") : Color.parseColor("#212121"));
                canvas.drawCircle(cx, cy, radius, circlePaint);
                
                // Draw inner circle for depth
                Paint innerCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
                innerCircle.setStyle(Paint.Style.STROKE);
                innerCircle.setStrokeWidth(1.5f);
                innerCircle.setColor(color == 1 ? Color.parseColor("#FF6659") : Color.parseColor("#424242"));
                canvas.drawCircle(cx, cy, radius * 0.85f, innerCircle);
                
                // Draw text
                Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
                textP.setColor(color == 1 ? Color.parseColor("#D32F2F") : Color.parseColor("#212121"));
                textP.setTextSize(radius * 1.15f);
                textP.setTextAlign(Paint.Align.CENTER);
                textP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                textP.setShadowLayer(2f, 1f, 1f, Color.parseColor("#40FFFFFF"));
                String text = (color == 2) ? pieceNamesBlack[pieceType] : pieceNames[pieceType];
                canvas.drawText(text, cx, cy + radius * 0.35f, textP);
            }
        }
        
        // Highlight selected piece with animated glow effect
        if (selectedR >= 0 && selectedC >= 0) {
            float cx = offsetX + selectedC * cellX;
            float cy = offsetY + selectedR * cellY;
            
            // Draw outer glow
            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setColor(Color.parseColor("#80FFD700"));
            glowPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, radius * 1.3f, glowPaint);
            
            // Draw selection ring
            Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            highlightPaint.setColor(Color.parseColor("#FFD700"));
            highlightPaint.setStyle(Paint.Style.STROKE);
            highlightPaint.setStrokeWidth(5f);
            highlightPaint.setShadowLayer(8f, 0f, 0f, Color.parseColor("#FFD700"));
            canvas.drawCircle(cx, cy, radius * 1.15f, highlightPaint);
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
        
        // Calculate proper cell size to maintain aspect ratio (10:9)
        float cellSize = Math.min(w / 8f, h / 9f);
        float boardWidth = cellSize * 8f;
        float boardHeight = cellSize * 9f;
        float offsetX = (w - boardWidth) / 2f;
        float offsetY = (h - boardHeight) / 2f;
        
        int c = Math.round((e.getX() - offsetX) / cellSize);
        int r = Math.round((e.getY() - offsetY) / cellSize);
        
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