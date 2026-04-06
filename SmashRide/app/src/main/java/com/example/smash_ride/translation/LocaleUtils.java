package com.example.smash_ride.translation;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.CompoundButton;

import com.google.mlkit.nl.translate.TranslateLanguage;

import java.util.Locale;

public final class LocaleUtils {
    private static final String TAG = "LocaleUtils";
    private LocaleUtils() {}

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

    public static Context applyAppLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Configuration config = context.getResources().getConfiguration();
        config.setLocale(locale);
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        return context;
    }

    public static String getEnglishTextForView(Context ctx, View view) {
        try {
            int id = view.getId();
            if (id == View.NO_ID) return getViewTextFallback(view);
            Resources res = (ctx != null) ? ctx.getResources() : view.getContext().getResources();
            String resName;
            try { resName = res.getResourceEntryName(id); } catch (Exception e) { resName = null; }
            int resId = 0;
            if (resName != null) resId = res.getIdentifier(resName, "string", (ctx != null) ? ctx.getPackageName() : view.getContext().getPackageName());
            if (resId != 0) {
                Configuration conf = new Configuration(res.getConfiguration());
                conf.setLocale(Locale.ENGLISH);
                CharSequence cs = (ctx != null) ? ctx.createConfigurationContext(conf).getText(resId) : view.getContext().createConfigurationContext(conf).getText(resId);
                return cs == null ? "" : cs.toString();
            } else {
                return "";
            }
        } catch (Exception e) {
            Log.d(TAG, "getEnglishTextForView fallback: " + e.getMessage());
            return "";
        }
    }

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
