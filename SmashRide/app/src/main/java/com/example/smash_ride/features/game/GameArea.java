package com.example.smash_ride.features.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import com.example.smash_ride.R;

public class GameArea {
    private final float centerX, centerY, radius;
    private Bitmap spriteSheet;

    // Configuración de la animación: Cuadrícula 4x5
    private final int totalFrames = 19;
    private final int columns = 4;
    private final int rows = 5;

    private int currentFrame = 0;
    private int frameWidth;
    private int frameHeight;

    // Control de tiempo para la velocidad de rotación
    private long lastFrameTime = 0;
    // Ajustado a 120ms para una rotación más lenta y natural
    private static final int FRAME_DURATION_MS = 120;

    private final Rect srcRect;
    private final Rect dstRect;

    public GameArea(Context context, float centerX, float centerY, float radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;

        // 1. Cargar el Sprite Sheet
        spriteSheet = BitmapFactory.decodeResource(context.getResources(), R.drawable.moon_spritesheet);

        // 2. Calcular dimensiones basadas en la cuadrícula 4x5
        frameWidth = spriteSheet.getWidth() / columns;
        frameHeight = spriteSheet.getHeight() / rows;

        // 3. Preparar rectángulos
        srcRect = new Rect();
        dstRect = new Rect(
                (int)(centerX - radius), (int)(centerY - radius),
                (int)(centerX + radius), (int)(centerY + radius)
        );
    }

    public void draw(Canvas canvas) {
        if (spriteSheet == null) return;

        // Lógica de cambio de frame
        long now = System.currentTimeMillis();
        if (now - lastFrameTime > FRAME_DURATION_MS) {
            currentFrame = (currentFrame + 1) % totalFrames; // Solo llega hasta el 18 (19 total)
            lastFrameTime = now;
        }

        // --- CÁLCULO DE POSICIÓN EN LA CUADRÍCULA ---
        // Ejemplo: Frame 5 -> Fila 1, Columna 1 (empezando de 0)
        int row = currentFrame / columns;
        int col = currentFrame % columns;

        int left = col * frameWidth;
        int top = row * frameHeight;
        int right = left + frameWidth;
        int bottom = top + frameHeight;

        srcRect.set(left, top, right, bottom);

        // Dibujar el frame actual
        canvas.drawBitmap(spriteSheet, srcRect, dstRect, null);
    }
}