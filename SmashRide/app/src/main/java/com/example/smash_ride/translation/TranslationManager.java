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

    public interface OnRawTranslationListener {
        void onTranslationComplete(String translatedText);
    }

    private TranslationManager(@NonNull Context appContext) {
        this.appCtx = appContext.getApplicationContext();
    }

    public static synchronized void initialize(@NonNull Context appContext) {
        if (instance == null) instance = new TranslationManager(appContext);
    }

    public static synchronized TranslationManager getInstance() {
        if (instance == null) throw new IllegalStateException("TranslationManager not initialized.");
        return instance;
    }

    public void setForcedLanguage(String lang) {
        this.forcedLanguage = lang;
    }

    public void bindActivity(@NonNull Activity activity) {
        this.boundActivityRef = new WeakReference<>(activity);
    }

    public void unbindActivity() {
        if (boundActivityRef != null) boundActivityRef.clear();
        this.registeredViews.clear();
    }

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

    private void postUpdateViewText(View view, String text) {
        mainHandler.post(() -> {
            if (view instanceof TextView) ((TextView) view).setText(text);
        });
    }

    public void setTargetFromAppLang(@Nullable String appLangCode) {
        setForcedLanguage(appLangCode);
        String mapped = LocaleUtils.mapAppLangToMlKit(appLangCode);
        if (mapped == null || mapped.equals(sourceLangMlKit)) {
            targetMlKit = null;
            return;
        }
        this.targetMlKit = mapped;
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangMlKit).setTargetLanguage(mapped).build();
        translator = Translation.getClient(options);
        translator.downloadModelIfNeeded();
    }

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

    public void translateRaw(String text, OnRawTranslationListener listener) {
        if (targetMlKit == null || translator == null) {
            listener.onTranslationComplete(text);
            return;
        }
        translator.translate(text)
                .addOnSuccessListener(listener::onTranslationComplete)
                .addOnFailureListener(e -> listener.onTranslationComplete(text));
    }

    // Añade esto al final de TranslationManager.java
    public void unregisterView(View view) {
        if (view != null) {
            registeredViews.remove(view);
            originalEnglishById.remove(view.getId());
        }
    }
}