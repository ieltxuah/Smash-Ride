package com.example.smash_ride.translation;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.CompoundButton;

import com.google.mlkit.nl.translate.TranslateLanguage;

import java.util.Locale;

/**
 * Clase de utilidad para gestionar la localización (Locale) y el mapeo de idiomas.
 * Proporciona métodos para aplicar cambios de idioma dinámicamente y obtener textos de vistas.
 */
public final class LocaleUtils {
    private static final String TAG = "LocaleUtils";

    // Constructor privado para evitar instanciación
    private LocaleUtils() {}

    /**
     * Aplica el código de idioma especificado al contexto de la aplicación.
     * Actualiza la configuración global de recursos para reflejar el nuevo idioma.
     *
     * @param context      Contexto sobre el que aplicar el cambio.
     * @param languageCode Código ISO del idioma (ej: "es", "en", "eu").
     * @return El contexto actualizado.
     */
    public static Context applyAppLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Configuration config = context.getResources().getConfiguration();
        config.setLocale(locale);
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        return context;
    }

    /**
     * Mapea un código de idioma de la aplicación al identificador correspondiente de ML Kit.
     * Los idiomas nativos (es, en, eu) devuelven null para indicar que deben usar recursos locales.
     *
     * @param abbrev Código de idioma de la aplicación.
     * @return El identificador de idioma de {@link TranslateLanguage} o null si es nativo.
     */
    public static String mapAppLangToMlKit(String abbrev) {
        if (abbrev == null) return null;
        switch (abbrev) {
            case "es": return null; // Español: usar fichero de recursos, no ML Kit
            case "fr": return TranslateLanguage.FRENCH;
            case "de": return TranslateLanguage.GERMAN;
            case "it": return TranslateLanguage.ITALIAN;
            case "zh": return TranslateLanguage.CHINESE;
            case "en": return null;
            case "eu": return null; // Euskera: usar fichero de recursos
            default: return abbrev;
        }
    }

    /** Comprueba si el idioma tiene soporte nativo en los recursos XML. */
    public static boolean isNativeLanguage(String lang) {
        return lang.equals("es") || lang.equals("eu") || lang.equals("en");
    }

    /**
     * Intenta obtener el texto visible de una vista de interfaz de usuario de forma genérica.
     * Soporta {@link TextView}, {@link Button} y {@link CompoundButton}.
     *
     * @param view La vista de la cual extraer el texto.
     * @return El texto de la vista o una cadena vacía si no es compatible o es nulo.
     */
    public static String getViewTextFallback(View view) {
        try {
            if (view instanceof TextView) {
                CharSequence t = ((TextView) view).getText();
                return t == null ? "" : t.toString();
            } else if (view instanceof Button) {
                CharSequence t = ((Button) view).getText();
                return t == null ? "" : t.toString();
            } else if (view instanceof CompoundButton) {
                CharSequence t = ((CompoundButton) view).getText();
                return t == null ? "" : t.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }
}
