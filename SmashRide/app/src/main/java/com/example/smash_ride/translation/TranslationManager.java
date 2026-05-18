package com.example.smash_ride.translation;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gestor centralizado para la traducción dinámica de la interfaz de usuario.
 * Utiliza ML Kit de Google para traducciones automáticas y el sistema de recursos de Android
 * para idiomas nativos. Implementa el patrón Singleton.
 */
public class TranslationManager {
    private static final String TAG = "DEBUG_TRANSLATION";
    private static TranslationManager instance;

    private final Context appCtx;
    private Translator translator;
    private final String sourceLangMlKit = TranslateLanguage.ENGLISH;
    private String targetMlKit;
    private String forcedLanguage = null;
    private final Map<Integer, String> originalEnglishById = new HashMap<>();
    private final List<View> registeredViews = new ArrayList<>();
    private WeakReference<Activity> boundActivityRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isModelDownloaded = false;

    /**
     * Interfaz para recibir el resultado de una traducción de texto plano.
     */
    public interface OnRawTranslationListener {
        /**
         * Llamado cuando la traducción se ha completado.
         *
         * @param translatedText El texto traducido (o el original si hubo error).
         */
        void onTranslationComplete(String translatedText);
    }

    private TranslationManager(@NonNull Context appContext) {
        this.appCtx = appContext.getApplicationContext();
    }

    /**
     * Inicializa la instancia única del gestor.
     *
     * @param appContext Contexto de la aplicación.
     */
    public static synchronized void initialize(@NonNull Context appContext) {
        if (instance == null) instance = new TranslationManager(appContext);
    }

    /**
     * Obtiene la instancia única del gestor.
     *
     * @return La instancia de {@link TranslationManager}.
     * @throws IllegalStateException si no se ha inicializado previamente.
     */
    public static synchronized TranslationManager getInstance() {
        if (instance == null) throw new IllegalStateException("TranslationManager not initialized.");
        return instance;
    }

    /**
     * Vincula una actividad al gestor para gestionar su ciclo de vida y contexto.
     *
     * @param activity Actividad que se desea vincular.
     */
    public void bindActivity(@NonNull Activity activity) {
        this.boundActivityRef = new WeakReference<>(activity);
    }

    /**
     * Desvincula la actividad actual y limpia las vistas registradas para evitar fugas de memoria.
     */
    public void unbindActivity() {
        if (boundActivityRef != null) boundActivityRef.clear();
        this.registeredViews.clear();
    }

    /**
     * Establece manualmente un idioma forzado para el gestor.
     *
     * @param lang Código de idioma (ej: "es", "en").
     */
    public void setForcedLanguage(String lang) {
        this.forcedLanguage = lang;
    }

    /**
     * Registra una serie de vistas para que sus textos sean gestionados por el traductor.
     * Almacena el texto original en inglés como base para futuras traducciones.
     *
     * @param views Vistas a registrar.
     */
    public synchronized void registerViews(@NonNull View... views) {
        for (View v : views) {
            if (v == null || v.getId() == View.NO_ID) continue;
            if (!registeredViews.contains(v)) {
                registeredViews.add(v);
                String idName = "unknown";
                try { idName = appCtx.getResources().getResourceEntryName(v.getId()); } catch (Exception ignored) {}

                String eng = "";
                try {
                    int resId = appCtx.getResources().getIdentifier(idName, "string", appCtx.getPackageName());
                    if (resId != 0) {
                        Configuration conf = new Configuration(appCtx.getResources().getConfiguration());
                        conf.setLocale(Locale.ENGLISH);
                        eng = appCtx.createConfigurationContext(conf).getString(resId);
                    }
                } catch (Exception ignored) {}
                originalEnglishById.put(v.getId(), (eng == null || eng.isEmpty()) ? LocaleUtils.getViewTextFallback(v) : eng);
            }
        }
    }

    /**
     * Escanea jerárquicamente un árbol de vistas y registra automáticamente aquellas que contienen texto.
     *
     * @param root Vista raíz desde la cual iniciar el escaneo.
     */
    public void scanAndRegisterViews(@NonNull View root) {
        Deque<View> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            View v = q.removeFirst();
            if (v == null) continue;
            if (v.getId() != View.NO_ID && (v instanceof TextView || v instanceof Button || v instanceof CompoundButton)) {
                registerViews(v);
            }
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) q.add(vg.getChildAt(i));
            }
        }
    }

    /**
     * Configura el idioma de destino basándose en el código de idioma de la aplicación.
     * Inicia la descarga del modelo de ML Kit si es necesario.
     *
     * @param appLangCode Código de idioma (ej: "fr", "de").
     */
    public void setTargetFromAppLang(@Nullable String appLangCode) {
        setForcedLanguage(appLangCode);
        String mapped = LocaleUtils.mapAppLangToMlKit(appLangCode);

        if (mapped == null || mapped.equals(sourceLangMlKit)) {
            targetMlKit = null;
            isModelDownloaded = true; // Inglés no requiere descarga
            return;
        }

        this.targetMlKit = mapped;
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangMlKit)
                .setTargetLanguage(mapped)
                .build();

        // Si ya existe un traductor, lo cerramos para liberar memoria
        if (translator != null) translator.close();
        translator = Translation.getClient(options);

        isModelDownloaded = false; // Bloqueamos traducciones hasta éxito

        translator.downloadModelIfNeeded()
                .addOnSuccessListener(v -> {
                    Log.d(TAG, "Modelo listo para: " + mapped);
                    isModelDownloaded = true;
                    // Ahora que el modelo existe físicamente, traducimos la UI
                    translateIfNeeded();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error descarga: " + e.getMessage());
                    isModelDownloaded = false;
                });
    }

    /**
     * Inicia el proceso de traducción para todas las vistas registradas si el modelo está listo.
     * Si no se requiere traducción automática, recarga los textos desde los recursos locales.
     */
    public void translateIfNeeded() {
        if (targetMlKit == null || translator == null) {
            reloadTextsFromResources();
            return;
        }
        for (View view : registeredViews) {
            String sourceText = originalEnglishById.get(view.getId());
            if (sourceText != null) {
                translator.translate(sourceText).addOnSuccessListener(translated -> postUpdateViewText(view, translated));
            }
        }
    }

    /**
     * Traduce una cadena de texto de forma asíncrona.
     *
     * @param text     Texto original en inglés.
     * @param listener Callback para recibir el resultado.
     */
    public void translateRaw(String text, OnRawTranslationListener listener) {
        // Si el modelo no está listo, devolvemos el texto original de inmediato
        if (targetMlKit == null || translator == null || !isModelDownloaded) {
            listener.onTranslationComplete(text);
            return;
        }

        translator.translate(text)
                .addOnSuccessListener(listener::onTranslationComplete)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error en translateRaw: " + e.getMessage());
                    listener.onTranslationComplete(text);
                });
    }

    /**
     * Recarga los textos de las vistas registradas utilizando los archivos de recursos (strings.xml)
     * según el idioma configurado actualmente.
     */
    public void reloadTextsFromResources() {
        String appLang = (forcedLanguage != null) ? forcedLanguage : "en";
        Activity activity = boundActivityRef != null ? boundActivityRef.get() : null;
        Context contextForResources = (activity != null) ? activity : appCtx;
        Context localizedContext = LocaleUtils.applyAppLocale(contextForResources, appLang);
        Resources res = localizedContext.getResources();

        for (View view : new ArrayList<>(registeredViews)) {
            try {
                String resName = appCtx.getResources().getResourceEntryName(view.getId());
                int resId = res.getIdentifier(resName, "string", appCtx.getPackageName());
                if (resId != 0) {
                    postUpdateViewText(view, res.getString(resId));
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Traduce un texto y lo muestra en un mensaje flotante (Toast).
     *
     * @param text Texto a traducir y mostrar.
     */
    public void showTranslatedToast(String text) {
        if (text == null || text.isEmpty()) return;

        // Intentamos obtener la Activity vinculada
        Activity currentActivity = (boundActivityRef != null) ? boundActivityRef.get() : null;
        // Si no hay activity, usamos el contexto de la aplicación (menos recomendado para Toasts pero seguro)
        Context context = (currentActivity != null) ? currentActivity : appCtx;

        translateRaw(text, translated -> {
            mainHandler.post(() -> {
                Toast.makeText(context, translated, Toast.LENGTH_SHORT).show();
            });
        });
    }

    /**
     * Actualiza el texto de una vista en el hilo principal.
     *
     * @param view Vista a actualizar.
     * @param text Nuevo texto traducido.
     */
    private void postUpdateViewText(View view, String text) {
        mainHandler.post(() -> {
            if (view instanceof TextView) ((TextView) view).setText(text);
        });
    }
}