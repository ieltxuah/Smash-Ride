package com.example.smash_ride.core.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import androidx.annotation.ColorInt;

/**
 * Clase de utilidad para manipular el color de los elementos visuales (Sprites/Icons).
 */
public class SpriteColorizer {

    /**
     * Aplica un filtro de color a un ImageView directamente.
     */
    public static void tintImageView(ImageView imageView, @ColorInt int color) {
        if (imageView != null) {
            imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
    }

    /**
     * Colorea un Bitmap y devuelve una nueva instancia.
     * Útil para sprites que se dibujan en un Canvas de juego.
     */
    public static Bitmap colorizeBitmap(Bitmap sourceBitmap, @ColorInt int color) {
        Bitmap resultBitmap = sourceBitmap.copy(sourceBitmap.getConfig(), true);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));

        Canvas canvas = new Canvas(resultBitmap);
        canvas.drawBitmap(resultBitmap, 0, 0, paint);

        return resultBitmap;
    }

    /**
     * Colorea un Drawable (recurso vectorial o png).
     */
    public static void tintDrawable(Drawable drawable, @ColorInt int color) {
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
    }
}