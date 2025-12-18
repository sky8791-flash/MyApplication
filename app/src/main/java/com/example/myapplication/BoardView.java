package com.example.myapplication;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.example.myapplication.GomokuEngine;

public class BoardView extends View {
    public interface OnPlaceListener { void onPlace(int r, int c); }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private GomokuEngine engine;
    private OnPlaceListener listener;
    private int size = 15;

    public BoardView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() {
        linePaint.setColor(Color.DKGRAY);
        linePaint.setStrokeWidth(3f);
        blackPaint.setColor(Color.BLACK);
        whitePaint.setColor(Color.WHITE);
        whitePaint.setShadowLayer(6f, 2f, 2f, Color.GRAY);
        setLayerType(LAYER_TYPE_SOFTWARE, whitePaint);
    }

    public void bindEngine(GomokuEngine e) { this.engine = e; this.size = e.getBoard().length; invalidate(); }
    public void setOnPlaceListener(OnPlaceListener l) { this.listener = l; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (engine == null) return;
        float w = getWidth(), h = getHeight();
        float cellX = w / (size - 1), cellY = h / (size - 1);

        // grid
        for (int i = 0; i < size; i++) {
            canvas.drawLine(0, i * cellY, w, i * cellY, linePaint);
            canvas.drawLine(i * cellX, 0, i * cellX, h, linePaint);
        }
        // stones
        int[][] board = engine.getBoard();
        float rStone = Math.min(cellX, cellY) * 0.4f;
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
            if (board[r][c] == 0) continue;
            float cx = c * cellX; float cy = r * cellY;
            canvas.drawCircle(cx, cy, rStone, board[r][c] == 1 ? blackPaint : whitePaint);
        }
        // last move marker
        GomokuEngine.Move lm = engine.getLastMove();
        if (lm != null) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.RED); p.setStrokeWidth(6f);
            float cx = lm.c * cellX, cy = lm.r * cellY;
            canvas.drawCircle(cx, cy, rStone * 0.35f, p);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (engine == null || listener == null) return false;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float w = getWidth(), h = getHeight();
            float cellX = w / (size - 1), cellY = h / (size - 1);
            int c = Math.round(e.getX() / cellX);
            int r = Math.round(e.getY() / cellY);
            listener.onPlace(r, c);
        }
        return true;
    }
}