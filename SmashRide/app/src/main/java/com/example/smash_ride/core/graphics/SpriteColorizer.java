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
 * Clase de utilidad para manipular el color de los elementos visuales (Sprites e Iconos).
 * Permite aplicar filtros de color dinámicos tanto a vistas como a mapas de bits.
 */
public class SpriteColorizer {

    /**
     * Aplica un filtro de color directamente a un {@link ImageView}.
     * Utiliza el modo {@link PorterDuff.Mode#SRC_IN} para teñir la imagen manteniendo su transparencia.
     *
     * @param imageView La vista de imagen a la que se aplicará el filtro.
     * @param color     El color con el que se desea teñir la imagen.
     */
    public static void tintImageView(ImageView imageView, @ColorInt int color) {
        if (imageView != null) {
            imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
    }

    /**
     * Crea una copia de un {@link Bitmap} y le aplica un filtro de color.
     * Es ideal para sprites que se dibujan manualmente sobre un Canvas en el bucle del juego.
     *
     * @param sourceBitmap El mapa de bits original.
     * @param color        El color con el que se desea teñir el mapa de bits.
     * @return Una nueva instancia de {@link Bitmap} con el color aplicado.
     */
    public static Bitmap colorizeBitmap(Bitmap sourceBitmap, @ColorInt int color) {
        Bitmap resultBitmap = sourceBitmap.copy(sourceBitmap.getConfig(), true);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));

        Canvas canvas = new Canvas(resultBitmap);
        canvas.drawBitmap(resultBitmap, 0, 0, paint);

        return resultBitmap;
    }
}