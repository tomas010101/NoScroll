# NoScroll

Aplicación Android para reducir el consumo de contenido de scroll infinito. Detecta determinadas secciones de Instagram y YouTube y ejecuta **Atrás** para salir de ellas, manteniendo disponible el resto de las aplicaciones.

NoScroll funciona **offline**, sin cuentas, servidores, anuncios ni telemetría.

## Qué permite bloquear

Cada bloqueo tiene un interruptor independiente y conserva su configuración entre sesiones:

- **Instagram Reels**: el reproductor de videos cortos.
- **Instagram Explorar y búsqueda**: la pestaña de la lupa, incluidas sus sugerencias.
- **YouTube Shorts**: el reproductor de videos cortos.

Los tres bloqueos están activados por defecto. El objetivo es permitir el feed, los perfiles, las publicaciones normales y los mensajes de Instagram, así como el inicio, la búsqueda y los videos normales de YouTube.

> **Estado: V1 en desarrollo.** Se han comprobado bloqueos en un teléfono real, pero la compatibilidad depende de la versión de las aplicaciones y del fabricante del dispositivo. No es un bloqueo infalible ni un modo estricto.

## Instalar y usar

**Requisito:** Android 8.0 (API 26) o posterior.

1. Compila el APK siguiendo la guía de desarrollo o utiliza un APK obtenido de una fuente de confianza.
2. Instálalo en el teléfono. Para instalar un APK manualmente, Android puede solicitar autorización para esa fuente.
3. Abre NoScroll y elige los bloqueos que quieres mantener activos.
4. Pulsa **Activar servicio de accesibilidad** y habilita **NoScroll → Usar NoScroll** en los ajustes del sistema.
5. Regresa a NoScroll y comprueba el estado del servicio.

La pantalla distingue entre servicio activo, deshabilitado y habilitado pero sin conexión. El indicador de servicio activo no garantiza que todas las versiones de Instagram o YouTube sean reconocidas; si apagas los tres interruptores, no se bloquea contenido.

Para detener los bloqueos, apaga sus interruptores o desactiva el servicio desde Accesibilidad. NoScroll no activa este permiso por su cuenta.

## Privacidad y permisos

El servicio lee la interfaz de `com.instagram.android` y `com.google.android.youtube` para reconocer las secciones seleccionadas. El análisis se realiza en el dispositivo.

- Sin permiso `INTERNET`, Analytics, Firebase ni telemetría.
- Sin overlays, VPN, root ni Shizuku.
- Solo se guardan las preferencias de bloqueo en `SharedPreferences`.
- Las compilaciones **DEBUG** escriben información de diagnóstico en Logcat, que puede incluir texto visible de esas aplicaciones. Revisa y anonimiza los logs antes de compartirlos.

## Desarrollo

### Preparar el entorno

- Android Studio con Android SDK Platform 36 instalado.
- JDK 21, utilizado para las compilaciones del proyecto.
- ADB si quieres instalar y probar desde la terminal.

Abre la carpeta del proyecto en Android Studio y sincroniza Gradle. El IDE puede generar `local.properties` con la ubicación del SDK. Si trabajas desde la terminal, configura allí `sdk.dir` con la ruta de tu instalación. Este archivo es local y no debe compartirse.

El proyecto incluye el wrapper de **Gradle 8.13** y utiliza **AGP 8.13.2**, **Kotlin 2.2.21**, **Jetpack Compose** y **Material 3**. Las versiones y dependencias se definen en [build.gradle.kts](build.gradle.kts) y [app/build.gradle.kts](app/build.gradle.kts). El bytecode Java/Kotlin apunta a Java 17.

La primera sincronización puede necesitar Internet para descargar herramientas y dependencias; la aplicación instalada no necesita conexión para funcionar.

### Compilar

Desde la raíz del proyecto:

```bash
./gradlew assembleDebug
```

El APK se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

En Windows, utiliza `gradlew.bat` en lugar de `./gradlew`.

### Instalar en un dispositivo

Con depuración USB habilitada y el teléfono autorizado:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Si `adb` no está en tu `PATH`, utiliza el ejecutable de la carpeta `platform-tools` del SDK. La opción `-r` actualiza la instalación conservando sus datos. Verifica el estado del servicio después de instalar o actualizar.

### Pruebas y análisis estático

```bash
./gradlew testDebugUnitTest --rerun-tasks
./gradlew lintDebug
```

Las pruebas JVM cubren los detectores y el intervalo entre intentos de bloqueo. Incluyen casos positivos y negativos, además de etiquetas observadas en el dispositivo. No sustituyen la validación con las aplicaciones reales.

## Arquitectura

Un único módulo Android, `app`, separa la interfaz, las preferencias y la detección:

```text
app/src/main/
├── AndroidManifest.xml
├── res/xml/accessibility_service_config.xml
└── java/com/tomas/noscroll/
    ├── ui/MainActivity.kt
    ├── preferences/BlockPreferences.kt
    ├── accessibility/
    │   ├── NoScrollAccessibilityService.kt
    │   ├── AccessibilityTreeReader.kt
    │   └── BlockCooldown.kt
    ├── detector/
    │   ├── ContentDetector.kt
    │   ├── DetectionResult.kt
    │   ├── TreeSnapshot.kt
    │   ├── ScoringDetector.kt
    │   ├── InstagramReelsDetector.kt
    │   ├── InstagramExploreDetector.kt
    │   └── YouTubeShortsDetector.kt
    └── debug/AccessibilityTreeLogger.kt
```

El flujo es: **evento de accesibilidad → ventana activa → snapshot inmutable → detector → preferencia → Atrás**. Los detectores trabajan con datos, sin retener nodos Android ni un `Context`.

El servicio escucha cambios de ventana, contenido, desplazamiento, clic y selección, limitado a los dos paquetes. Agrupa eventos durante 120 ms y separa los intentos de Atrás por al menos 1,2 segundos. La lectura está limitada a 450 nodos y 35 niveles de profundidad; un árbol incompleto no autoriza un bloqueo.

### Reglas de detección

- **Reels y Shorts:** requieren un contenedor reconocido por ID, visible y suficientemente grande, señales dentro del mismo subárbol y al menos dos controles distintos. El umbral es **9 puntos**. Por ejemplo: contenedor (5) + audio o Remix (2) + dos controles (1 cada uno).
- **Explorar:** requiere la pestaña `search_tab` seleccionada (4), el pager principal (2) y el control de búsqueda visible dentro de él (3). La lupa presente en la navegación por sí sola no dispara el bloqueo.

Los IDs, etiquetas y pesos viven en [detector/](app/src/main/java/com/tomas/noscroll/detector/). Prioriza señales específicas y capturas reales al modificar estas reglas; bajar el umbral puede introducir falsos positivos.

## Diagnóstico y limitaciones

Para observar una compilación DEBUG:

```bash
adb logcat -s NOSCROLL_DETECTOR:D NOSCROLL_TREE:D '*:S'
adb shell dumpsys accessibility
adb shell dumpsys activity exit-info com.tomas.noscroll
```

`NOSCROLL_DETECTOR` registra conexión, eventos, disponibilidad de la raíz, tamaño del snapshot, puntuación, señales y acción. `BACK_RESULT returned=true` indica que Android aceptó Atrás; comprueba también que la pantalla cambió.

`NOSCROLL_TREE` vuelca el árbol aunque el detector no reconozca contenido: IDs, textos, descripciones, selección, visibilidad y geometría. Limita cada volcado a 120 nodos y uno cada cinco segundos.

| Situación | Qué revisar |
| --- | --- |
| Habilitado en Ajustes, pero sin conexión | Estado del servicio y cierres del proceso. Desactiva y reactiva el servicio si es necesario. |
| Deja de funcionar tras limpiar aplicaciones | En el teléfono probado, `SwipeUpClean` y `OneKeyClean` terminaron NoScroll. Evita cerrar la app desde recientes durante las pruebas y revisa las opciones de batería del fabricante. |
| No llegan eventos al cambiar de app | Reproduce la transición y observa Logcat sin reiniciar el servicio. Se ha observado recepción intermitente; un bloqueo aislado no demuestra estabilidad. |
| `NO_PLAYER_CONTAINER` o `INSUFFICIENT_SIGNALS` | Contrasta el árbol con los IDs y etiquetas del detector para esa versión e idioma. |
| `INCOMPLETE_TREE` | La lectura falló, cambió la ventana o se alcanzaron los límites; se permite el contenido para evitar decisiones con datos incompletos. |
| `DISABLED` o `COOLDOWN` | Revisa el interruptor correspondiente o espera a que termine el intervalo entre intentos. |

Los IDs internos de Instagram y YouTube no son una API estable. Una actualización, un idioma diferente o una variante de interfaz puede requerir adaptar los detectores. UIAutomator puede desconectar y reconectar el servicio durante una inspección: valida también sin esa herramienta para no ocultar problemas de recepción.

## Cómo validar o contribuir

Antes de proponer una modificación, reproduce el problema y registra la versión de Android, fabricante, versión de la aplicación, idioma y pasos exactos. Comparte únicamente logs relevantes y anonimizados.

Para cambios de detección:

1. Identifica la etapa que falla: eventos, raíz, lectura, scoring o acción Atrás.
2. Añade una prueba que represente el caso observado y casos cercanos que deban permitirse.
3. Ejecuta las pruebas, compila e instala el APK.
4. Verifica Reels, Explorar y Shorts; comprueba también feed, perfiles, publicaciones y mensajes de Instagram, e inicio, búsqueda, suscripciones y videos normales de YouTube.
5. Alterna entre ambas aplicaciones varias veces, prueba los interruptores por separado y verifica que las preferencias persistan.

Mantén los cambios acotados y describe qué se verificó realmente en el dispositivo. El alcance actual no incluye estadísticas, rachas, PIN, horarios ni modo estricto.
