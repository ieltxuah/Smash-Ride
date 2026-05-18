package com.example.smash_ride.core.ui;

import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Clase base para todas las actividades de la aplicación.
 * Proporciona una implementación centralizada del modo inmersivo para ocultar
 * las barras del sistema y maximizar el área de juego.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Volver a aplicar el modo inmersivo cuando la ventana recupera el foco
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    /**
     * Aplica el modo inmersivo (pantalla completa) ocultando las barras de estado
     * y de navegación. Se adapta según la versión de la API de Android.
     */
    protected void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Lógica para Android 11 (API 30) o superior
            final WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Lógica para versiones anteriores (API 31 hacia abajo)
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }
}