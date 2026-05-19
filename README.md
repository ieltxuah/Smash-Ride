# Smash Ride
Smash Ride es un juego móvil multijugador online realizado para el Proyecto de Fin de Ciclo de Grado Superior de Desarrollo de Aplicaciones Multiplataforma (FP DAM). Cuatro estrellas compiten en la superficie lunar por sobrevivir; los jugadores deben chocar contra las otras estrellas para expulsarlas fuera de la zona segura de la Luna. El juego fue desarrollado en Android Studio usando Java y utiliza Firebase (Realtime Database para las partidas y Firestore para rankings), Google Sign-In para autenticación de usuarios y ML Kit para traducciones dinámicas.

## Índice
- Descripción
- Capturas
- Características principales
- Tecnologías y herramientas
- Requisitos
- Instalación y ejecución
- Créditos

## Descripción
Smash Ride es un juego móvil multijugador online (partidas fijas de 4 jugadores) donde cada jugador controla una estrella en la superficie lunar. El objetivo es empujar (chocar) a las demás estrellas fuera de la zona segura de la Luna. Hay dos modos disponibles: Modo Vidas y Modo Contrarreloj. La sincronización y la lógica de servidor se manejan mediante Firebase (Realtime Database y Firestore); el proyecto ya incluye google-services.json.

## Capturas
<!-- Pdte Subir Capturas -->

## Características principales
- Partidas multijugador en tiempo real, 4 jugadores fijos por sala.
- Modo Vidas y Modo Contrarreloj.
- Controles táctiles: toque para impulso, deslizar para dirección.
- Lobby y matchmaking gestionados por Firebase.
- Sistema de puntuaciones.

## Tecnologías y herramientas
- Lenguaje: Java.
- IDE: Android Studio.
- Android SDK: minSdkVersion: 31 (Android 12), targetSdkVersion: 36 (Android 16).
- Backend / Networking: Firebase (Realtime Database o Firestore). google-services.json incluido.
- Autenticación: Firebase Authentication (anónima o Google).
- Traducciones: ML Kit Translate.

## Requisitos
- Android 12 (API 31) o superior.
- Conexión a Internet para multijugador.
- Permisos: Notificaciones.

## Instalación y ejecución
1. Clonar el repositorio:
   ```bash
   git clone https://github.com/ieltxuah/Smash-Ride.git
   ```
2. Abrir el proyecto en Android Studio.
3. Verificar que app/google-services.json está presente (incluido en el repo).
4. Sincronizar Gradle y comprobar dependencias de Firebase.
5. Ejecutar en emulador o dispositivo: Run -> app.

## Créditos
Desarrollado por Ieltxu Albin y Cristobal Greenfield
