package com.example.smash_ride.translation;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
    private static final String TAG = "TranslationManager";
    private static TranslationManager instance;

    private final Context appCtx;
    private Translator translator;
    private final String sourceLangMlKit = TranslateLanguage.ENGLISH;
    private String targetMlKit; // null => rely on resources
    private final Map<Integer, String> originalEnglishById = new HashMap<>();
    private final List<View> registeredViews = new ArrayList<>();
    private WeakReference<Activity> boundActivityRef;
    private TranslationListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TranslationManager(@NonNull Context appContext) {
        this.appCtx = appContext.getApplicationContext();
    }

    public static synchronized void initialize(@NonNull Context appContext) {
        if (instance == null) instance = new TranslationManager(appContext);
    }

    public static synchronized TranslationManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TranslationManager not initialized. Call initialize(app) in Application.onCreate()");
        }
        return instance;
    }

    public synchronized void shutdown() {
        closeTranslatorIfAny();
        registeredViews.clear();
        originalEnglishById.clear();
        listener = null;
        boundActivityRef = null;
        targetMlKit = null;
    }

    public void bindActivity(@NonNull Activity activity) {
        boundActivityRef = new WeakReference<>(activity);
    }

    public void unbindActivity() {
        if (boundActivityRef != null) boundActivityRef.clear();
        boundActivityRef = null;
    }

    public void setListener(@Nullable TranslationListener l) {
        this.listener = l;
    }

    public void setTargetFromAppLang(@Nullable String appLangCode) {
        String mapped = LocaleUtils.mapAppLangToMlKit(appLangCode);
        if (mapped == null || mapped.equals(sourceLangMlKit)) {
            targetMlKit = null;
            closeTranslatorIfAny();
            return;
        }
        if (mapped.equals(this.targetMlKit) && translator != null) {
            return;
        }
        this.targetMlKit = mapped;
        setupTranslatorIfNeeded(sourceLangMlKit, mapped);
    }

    private void setupTranslatorIfNeeded(String sourceLanguage, String targetLanguage) {
        closeTranslatorIfAny();
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build();
        translator = Translation.getClient(options);
    }

    public synchronized void registerViews(@NonNull View... views) {
        for (View v : views) {
            if (v == null) continue;
            if (v.getId() == View.NO_ID) continue;
            if (!registeredViews.contains(v)) {
                registeredViews.add(v);

                String eng = "";
                try {
                    String resourceName = null;
                    try { resourceName = appCtx.getResources().getResourceEntryName(v.getId()); } catch (Exception ignored) {}
                    if (resourceName != null) {
                        int resId = appCtx.getResources().getIdentifier(resourceName, "string", appCtx.getPackageName());
                        if (resId != 0) {
                            Configuration conf = new Configuration(appCtx.getResources().getConfiguration());
                            conf.setLocale(Locale.ENGLISH);
                            CharSequence cs = appCtx.createConfigurationContext(conf).getText(resId);
                            eng = cs == null ? "" : cs.toString();
                        }
                    }
                } catch (Exception ignored) {}

                if (eng == null || eng.isEmpty()) {
                    eng = LocaleUtils.getViewTextFallback(v);
                }

                originalEnglishById.put(v.getId(), eng);
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
                if (!registeredViews.contains(v)) {
                    registerViews(v);
                }
            }
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) q.add(vg.getChildAt(i));
            }
        }
    }

    public void translateIfNeeded() {
        if (targetMlKit == null || translator == null) {
            // When no ML translation is needed, ensure we display localized resources
            restoreFromResources();
            if (listener != null) listener.onNoTranslationNeeded();
            return;
        }
        translator.downloadModelIfNeeded()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Model downloaded.");
                    if (listener != null) listener.onModelDownloaded(targetMlKit);
                    translateAllViewsUsingEnglishSource();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Model download failed: " + e.getMessage());
                    if (listener != null) listener.onFailure(e);
                    restoreFromResources();
                });
    }

    private void translateAllViewsUsingEnglishSource() {
        if (translator == null) return;
        List<View> snapshot = new ArrayList<>(registeredViews);
        for (View view : snapshot) {
            String sourceText = originalEnglishById.get(view.getId());
            if (sourceText == null || sourceText.isEmpty()) {
                sourceText = LocaleUtils.getEnglishTextForView(appCtx, view);
                if (sourceText == null || sourceText.isEmpty()) {
                    sourceText = LocaleUtils.getViewTextFallback(view);
                }
            }
            translateTextToView(sourceText, view);
        }
    }

    private void translateTextToView(String text, View view) {
        if (translator == null) return;
        final String toTranslate = text == null ? "" : text;
        translator.translate(toTranslate)
                .addOnSuccessListener(translatedText -> {
                    postUpdateViewText(view, translatedText);
                    if (listener != null) listener.onTranslated(view, translatedText);
                })
                .addOnFailureListener(e -> {
                    String fallback = getLocalizedOrOriginalForView(view);
                    postUpdateViewText(view, fallback);
                    if (listener != null) listener.onFailure(e);
                });
    }

    private String getCurrentAppLangFromPrefs() {
        try {
            SharedPreferences prefs = appCtx.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            return prefs.getString("language", "en");
        } catch (Exception e) {
            return "en";
        }
    }

    private String getLocalizedOrOriginalForView(View view) {
        try {
            String appLang = getCurrentAppLangFromPrefs();
            Activity a = boundActivityRef == null ? null : boundActivityRef.get();
            Context baseCtx = (a != null) ? a : appCtx;
            Context localized = LocaleUtils.applyAppLocale(baseCtx, appLang);
            Resources res = localized.getResources();
            String resName = null;
            try { resName = res.getResourceEntryName(view.getId()); } catch (Exception ignored) {}
            if (resName != null) {
                int resId = res.getIdentifier(resName, "string", baseCtx.getPackageName());
                if (resId != 0) return localized.getString(resId);
            }
        } catch (Exception ignored) {}
        String orig = originalEnglishById.get(view.getId());
        return orig != null ? orig : "";
    }

    private void postUpdateViewText(View view, String text) {
        Activity a = boundActivityRef == null ? null : boundActivityRef.get();
        if (a != null && !a.isFinishing()) {
            a.runOnUiThread(() -> applyTextToView(view, text));
        } else {
            mainHandler.post(() -> applyTextToView(view, text));
        }
    }

    private void applyTextToView(View view, String text) {
        if (view instanceof TextView) {
            ((TextView) view).setText(text);
        } else if (view instanceof Button) {
            ((Button) view).setText(text);
        } else if (view instanceof CompoundButton) {
            try { ((CompoundButton) view).setText(text); } catch (Exception ignored) {}
        }
    }

    public void restoreFromResources() {
        String appLang = getCurrentAppLangFromPrefs();
        Activity a = boundActivityRef == null ? null : boundActivityRef.get();
        Context baseCtx = (a != null) ? a : appCtx;
        Context localized = LocaleUtils.applyAppLocale(baseCtx, appLang);
        Resources res = localized.getResources();

        for (View view : new ArrayList<>(registeredViews)) {
            try {
                String resName = null;
                try { resName = res.getResourceEntryName(view.getId()); } catch (Exception ignored) {}
                if (resName != null) {
                    int resId = res.getIdentifier(resName, "string", baseCtx.getPackageName());
                    if (resId != 0) {
                        final String localizedStr = localized.getString(resId);
                        postUpdateViewText(view, localizedStr);
                        continue;
                    }
                }
            } catch (Exception ignored) {}
            String orig = originalEnglishById.get(view.getId());
            if (orig != null) postUpdateViewText(view, orig);
        }
        if (listener != null) listener.onRestoredFromResources();
    }

    public void reloadTextsFromResources() {
        restoreFromResources();
    }

    private void closeTranslatorIfAny() {
        if (translator != null) {
            try { translator.close(); } catch (Exception ignored) {}
            translator = null;
        }
    }

    public void close() {
        closeTranslatorIfAny();
    }

    public String getStringForResId(int resId) {
        try {
            if (resId == 0) return "";
            return appCtx.getString(resId);
        } catch (Exception e) {
            return "";
        }
    }

    public interface TranslationListener {
        void onModelDownloaded(String targetLanguage);
        void onTranslated(View view, String translatedText);
        void onFailure(@NonNull Exception e);
        void onRestoredFromResources();
        void onNoTranslationNeeded();
    }
}
