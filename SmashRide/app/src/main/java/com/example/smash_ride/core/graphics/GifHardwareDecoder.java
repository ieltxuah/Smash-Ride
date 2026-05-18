package com.example.smash_ride.core.graphics;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;

/**
 * Utilidad para la decodificación de archivos GIF utilizando aceleración por hardware.
 * Esta clase aprovecha las capacidades de {@link ImageDecoder} introducidas en la API 28.
 */
public class GifHardwareDecoder {

    /**
     * Carga un recurso GIF en un {@link ImageView} utilizando decodificación por hardware.
     * Si la versión de Android es inferior a la API 28 o la decodificación falla,
     * el ImageView se oculta para permitir que se vea el fondo de respaldo definido en el XML.
     *
     * @param context   Contexto para acceder a los recursos.
     * @param gifView   Vista donde se cargará el GIF.
     * @param gifResId  ID del recurso raw que contiene el GIF.
     */
    public static void loadGif(Context context, ImageView gifView, int gifResId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(context.getResources(), gifResId);
                Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE); // Optimización de memoria
                });

                gifView.setImageDrawable(drawable);
                gifView.setVisibility(View.VISIBLE);

                if (drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) drawable).start();
                }
            } catch (IOException e) {
                Log.e("GifDecoder", "Error decodificando GIF, usando fondo estático", e);
                gifView.setVisibility(View.GONE);
            }
        } else {
            // Versiones antiguas de Android no soportan ImageDecoder nativo para GIFs
            gifView.setVisibility(View.GONE);
        }
    }
}