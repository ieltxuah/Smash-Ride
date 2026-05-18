package com.example.smash_ride.framework.notifications;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.smash_ride.presentation.main.MainActivity;
import com.example.smash_ride.MyApplication;
import com.example.smash_ride.R;

/**
 * Trabajador de segundo plano que gestiona la creación y el envío de notificaciones
 * de recordatorio al usuario. Extiende {@link Worker} para su uso con WorkManager.
 */
public class NotificationWorker extends Worker {
    private static final int NOTIF_ID = 1001;
    private static final String TAG = "NotificationWorker";

    /**
     * Constructor del trabajador.
     *
     * @param context Contexto de la aplicación.
     * @param params  Parámetros de ejecución del trabajador.
     */
    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /**
     * Ejecuta la tarea del trabajador: crea un Intent para abrir la aplicación,
     * construye la notificación y la envía si se tienen los permisos necesarios.
     *
     * @return El resultado de la operación (siempre {@link Result#success()} en este caso).
     */
    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        // Crear el intent para abrir la actividad principal al pulsar la notificación
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        // Construcción de la notificación
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MyApplication.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // usa ic_launcher o tu drawable
                .setContentTitle("Te echamos de menos")
                .setContentText("Vuelve a jugar — ¡hay novedades esperándote!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Si la API es 33+, verificar permiso POST_NOTIFICATIONS antes de notificar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "No POST_NOTIFICATIONS permission - skip notify()");
                return Result.success();
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException posting notification", e);
        }

        return Result.success();
    }
}
