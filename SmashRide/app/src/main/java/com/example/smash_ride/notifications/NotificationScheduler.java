package com.example.smash_ride.notifications;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.example.smash_ride.core.constants.AppConstants;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class NotificationScheduler {
    private final Context context;
    private final SharedPreferences prefs;

    public NotificationScheduler(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(AppConstants.PREFS_NOTIF, Context.MODE_PRIVATE);
    }

    public void scheduleReturnReminder(int delayMinutes) {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueue(work);
        prefs.edit().putString(AppConstants.KEY_WORK_ID, work.getId().toString()).apply();
    }

    public void cancelPendingReminder() {
        String id = prefs.getString(AppConstants.KEY_WORK_ID, null);
        if (id != null) {
            WorkManager.getInstance(context).cancelWorkById(UUID.fromString(id));
            prefs.edit().remove(AppConstants.KEY_WORK_ID).apply();
        }
    }

    public void requestPermissions(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, requestCode);
            }
        }
    }
}