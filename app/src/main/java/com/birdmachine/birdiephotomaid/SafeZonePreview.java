package com.birdmachine.birdiephotomaid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class SafeZonePreview extends View {
    public interface Listener { void onChanged(int top, int bottom, int left, int right); }

    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blocked = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint safe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int top, bottom, left, right;
    private Listener listener;
    private Edge dragging = Edge.NONE;

    private enum Edge { NONE, TOP, BOTTOM, LEFT, RIGHT }

    public SafeZonePreview(Context context) {
        super(context);
        border.setColor(Color.rgb(68, 43, 32));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        blocked.setColor(Color.argb(115, 210, 62, 62));
        safe.setColor(Color.argb(80, 77, 181, 107));
        setMinimumHeight(Math.round(dp(260)));
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setZones(int top, int bottom, int left, int right) {
        this.top = clamp(top); this.bottom = clamp(bottom);
        this.left = clamp(left); this.right = clamp(right);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(12);
        RectF phone = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawRoundRect(phone, dp(22), dp(22), border);

        float usableW = phone.width();
        float usableH = phone.height();
        float x1 = phone.left + usableW * left / 100f;
        float x2 = phone.right - usableW * right / 100f;
        float y1 = phone.top + usableH * top / 100f;
        float y2 = phone.bottom - usableH * bottom / 100f;
        if (x2 > x1 && y2 > y1) canvas.drawRect(x1, y1, x2, y2, safe);
        if (top > 0) canvas.drawRect(phone.left, phone.top, phone.right, y1, blocked);
        if (bottom > 0) canvas.drawRect(phone.left, y2, phone.right, phone.bottom, blocked);
        if (left > 0) canvas.drawRect(phone.left, y1, x1, y2, blocked);
        if (right > 0) canvas.drawRect(x2, y1, phone.right, y2, blocked);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float pad = dp(12);
        float width = Math.max(1f, getWidth() - 2 * pad);
        float height = Math.max(1f, getHeight() - 2 * pad);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            dragging = nearestEdge(event.getX(), event.getY(), pad, width, height);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            switch (dragging) {
                case TOP -> top = clamp(Math.round((event.getY() - pad) / height * 100f));
                case BOTTOM -> bottom = clamp(Math.round((pad + height - event.getY()) / height * 100f));
                case LEFT -> left = clamp(Math.round((event.getX() - pad) / width * 100f));
                case RIGHT -> right = clamp(Math.round((pad + width - event.getX()) / width * 100f));
            }
            preventOverlap();
            invalidate();
            if (listener != null) listener.onChanged(top, bottom, left, right);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            dragging = Edge.NONE;
            return true;
        }
        return super.onTouchEvent(event);
    }

    private Edge nearestEdge(float x, float y, float pad, float width, float height) {
        float xLeft = pad + width * left / 100f;
        float xRight = pad + width * (1f - right / 100f);
        float yTop = pad + height * top / 100f;
        float yBottom = pad + height * (1f - bottom / 100f);
        float dl = Math.abs(x - xLeft), dr = Math.abs(x - xRight);
        float dt = Math.abs(y - yTop), db = Math.abs(y - yBottom);
        float min = Math.min(Math.min(dl, dr), Math.min(dt, db));
        if (min == dt) return Edge.TOP;
        if (min == db) return Edge.BOTTOM;
        if (min == dl) return Edge.LEFT;
        return Edge.RIGHT;
    }

    private void preventOverlap() {
        if (top + bottom > 80) {
            if (dragging == Edge.TOP) top = 80 - bottom; else bottom = 80 - top;
        }
        if (left + right > 80) {
            if (dragging == Edge.LEFT) left = 80 - right; else right = 80 - left;
        }
    }

    private int clamp(int value) { return Math.max(0, Math.min(45, value)); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
