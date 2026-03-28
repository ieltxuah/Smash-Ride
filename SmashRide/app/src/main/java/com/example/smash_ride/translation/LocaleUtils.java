package com.example.smash_ride.translation;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.mlkit.nl.translate.TranslateLanguage;

import java.util.Locale;

public final class LocaleUtils {
    private static final String TAG = "LocaleUtils";
    private LocaleUtils() {}

    public static String mapAppLangToMlKit(String abbrev) {
        if (abbrev == null) return null;
        switch (abbrev) {
            case "es": return null;
            case "fr": return TranslateLanguage.FRENCH;
            case "de": return TranslateLanguage.GERMAN;
            case "it": return TranslateLanguage.ITALIAN;
            case "zh": return TranslateLanguage.CHINESE;
            case "en": return null;
            case "eu": return null;
            default: return abbrev;
        }
    }

    public static void applyAppLocale(Context ctx, String langCode) {
        if (langCode == null) langCode = "en";
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Resources res = ctx.getResources();
        Configuration conf = new Configuration(res.getConfiguration());
        conf.setLocale(locale);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            ctx = ctx.createConfigurationContext(conf);
        }
        res.updateConfiguration(conf, res.getDisplayMetrics());
    }

    public static String getEnglishTextForView(Context ctx, View view) {
        try {
            int id = view.getId();
            if (id == View.NO_ID) return getViewText(view);
            Resources res = ctx.getResources();
            String resName = res.getResourceEntryName(id);
            int resId = res.getIdentifier(resName, "string", ctx.getPackageName());
            if (resId != 0) {
                Configuration conf = new Configuration(res.getConfiguration());
                conf.setLocale(Locale.ENGLISH);
                CharSequence cs = ctx.createConfigurationContext(conf).getText(resId);
                return cs == null ? "" : cs.toString();
            } else {
                return getViewText(view);
            }
        } catch (Exception e) {
            Log.d(TAG, "getEnglishTextForView fallback: " + e.getMessage());
            return getViewText(view);
        }
    }

    private static String getViewText(View view) {
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            return t == null ? "" : t.toString();
        } else if (view instanceof Button) {
            CharSequence t = ((Button) view).getText();
            return t == null ? "" : t.toString();
        }
        return "";
    }
}
