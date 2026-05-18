package com.example.smash_ride.framework.notifications;

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

/**
 * Clase encargada de programar y gestionar las notificaciones locales de la aplicación.
 * Utiliza {@link WorkManager} para asegurar la ejecución de tareas en segundo plano.
 */
public class NotificationScheduler {
    private final Context context;
    private final SharedPreferences prefs;

    /**
     * Constructor de la clase.
     *
     * @param context Contexto de la aplicación o actividad.
     */
    public NotificationScheduler(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(AppConstants.PREFS_NOTIF, Context.MODE_PRIVATE);
    }

    /**
     * Programa un recordatorio para que el usuario vuelva a jugar después de un tiempo determinado.
     *
     * @param delayMinutes Tiempo de espera en minutos antes de mostrar la notificación.
     */
    public void scheduleReturnReminder(int delayMinutes) {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueue(work);
        prefs.edit().putString(AppConstants.KEY_WORK_ID, work.getId().toString()).apply();
    }

    /**
     * Cancela cualquier recordatorio que esté pendiente de mostrarse.
     */
    public void cancelPendingReminder() {
        String id = prefs.getString(AppConstants.KEY_WORK_ID, null);
        if (id != null) {
            WorkManager.getInstance(context).cancelWorkById(UUID.fromString(id));
            prefs.edit().remove(AppConstants.KEY_WORK_ID).apply();
        }
    }

    /**
     * Solicita los permisos necesarios para enviar notificaciones en Android 13 (API 33) o superior.
     *
     * @param activity    Actividad desde la que se solicita el permiso.
     * @param requestCode Código de solicitud para identificar la respuesta del permiso.
     */
    public void requestPermissions(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, requestCode);
            }
        }
    }
}