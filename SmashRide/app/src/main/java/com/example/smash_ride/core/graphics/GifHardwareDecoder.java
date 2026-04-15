package com.example.smash_ride.core.graphics;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;

public class GifHardwareDecoder {

    /**
     * Carga un GIF usando hardware acceleration (API 28+).
     * Si falla o la versión es antigua, oculta el ImageView del GIF para mostrar el fallback del XML.
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