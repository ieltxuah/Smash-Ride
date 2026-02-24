package com.example.smash_ride;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class GameArea {
    private float centerX;
    private float centerY;
    private final float radius;
    private final Paint trackPaint;

    public GameArea(float centerX, float centerY, float radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        trackPaint = createPaint(Color.GRAY);
    }

    private Paint createPaint(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        return paint;
    }

    public void draw(Canvas canvas) {
        canvas.drawCircle(centerX, centerY, radius, trackPaint);
    }
}
