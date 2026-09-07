# NoScroll V1

Aplicación personal Android offline para salir de Instagram Reels y YouTube Shorts con Atrás.
Proyecto creado desde cero exclusivamente en `/home/tomas/AndroidStudioProjects/NoScroll`.
No había carpeta NoScroll ni archivos preexistentes: todos los archivos del proyecto son nuevos.

## Compilar e instalar

Versiones fijas: Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin y plugin Compose 2.2.21,
Compose BOM 2025.08.01, Activity Compose 1.10.1. Material 3 se resuelve mediante el BOM.
minSdk 26; compileSdk/targetSdk 36; bytecode Java 17. Compilado con JDK 21 instalado.
La primera compilación puede descargar dependencias; la aplicación instalada no requiere red.

```bash
cd /home/tomas/AndroidStudioProjects/NoScroll
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
/home/tomas/Android/Sdk/platform-tools/adb install -r /home/tomas/AndroidStudioProjects/NoScroll/app/build/outputs/apk/debug/app-debug.apk
```

También se puede copiar el APK al teléfono y abrirlo, autorizando la instalación desde esa fuente.
Abrir NoScroll → Activar servicio de accesibilidad → NoScroll → habilitar manualmente.
Si Android restringe una app instalada por APK: Ajustes → Aplicaciones → NoScroll → menú ⋮ →
Permitir ajustes restringidos (si esa opción existe en tu teléfono), y volver a Accesibilidad. [Ayuda oficial de Android](https://support.google.com/android/answer/12623953?hl=es).
Al regresar a NoScroll debe aparecer «✓ Protección activa» y «Servicio de accesibilidad activo».
Ambos switches empiezan activados. SharedPreferences conserva cada cambio y el servicio lo consulta antes de actuar.
El estado indica servicio habilitado en Android; si ambos switches están apagados no se bloquea contenido.

## Validación realizada

- `./gradlew assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL.
- 28 pruebas JVM: 26 de detección y 2 de cooldown; cero fallos, errores u omisiones.
- Lint: cero errores, siete advertencias (atributo de API 31, versiones disponibles,
  reglas de backup de Android 12 y sugerencias KTX). No impiden compilar.
- APK verificado en `/home/tomas/AndroidStudioProjects/NoScroll/app/build/outputs/apk/debug/app-debug.apk`;
  9.868.022 bytes, firma APK v2 validada con apksigner.
- Permisos del APK inspeccionados con aapt: sin INTERNET ni SYSTEM_ALERT_WINDOW.
  AndroidX agrega únicamente su permiso interno DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION.
- ADB no encontró dispositivos conectados. No se ejecutaron pruebas en Instagram/YouTube reales.

## Arquitectura y archivos

```text
NoScroll/
├── .gitignore
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── local.properties                 # SDK local; ignorado por Git
├── gradlew / gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── drawable/ic_noscroll.xml
        │   │   ├── values/strings.xml
        │   │   ├── values/themes.xml
        │   │   └── xml/accessibility_service_config.xml
        │   └── java/com/tomas/noscroll/
        │       ├── accessibility/
        │       │   ├── NoScrollAccessibilityService.kt
        │       │   ├── AccessibilityTreeReader.kt
        │       │   └── BlockCooldown.kt
        │       ├── detector/
        │       │   ├── ContentDetector.kt
        │       │   ├── DetectionResult.kt
        │       │   ├── TreeSnapshot.kt
        │       │   ├── ScoringDetector.kt
        │       │   ├── InstagramReelsDetector.kt
        │       │   └── YouTubeShortsDetector.kt
        │       ├── preferences/BlockPreferences.kt
        │       ├── debug/AccessibilityTreeLogger.kt
        │       └── ui/MainActivity.kt
        └── test/java/com/tomas/noscroll/
            ├── detector/ContentDetectorTest.kt
            └── accessibility/BlockCooldownTest.kt
```

El servicio filtra eventos, obtiene la ventana actual y aplica la decisión. El lector crea un snapshot
inmutable; los detectores sólo trabajan con datos, sin Context ni referencias a nodos Android.
La interfaz Compose/Material 3 consulta el servicio habilitado al reanudarse.

## Detección conservadora

**Limitación fundamental:** los IDs internos de Instagram y YouTube no son una API estable.
Las listas iniciales son heurísticas, no están verificadas contra el teléfono del usuario.
Si la versión instalada no expone estos contenedores, NoScroll dejará pasar el contenido.
No hay fallback basado solamente en palabras, porque aumentaría los falsos positivos.
Las pruebas sintéticas verifican las reglas; no demuestran compatibilidad con las apps reales.

Requisitos comunes, además del score:

- Árbol completo, paquete correcto y contenedor visible de reproducción reconocido por ID exacto.
- El contenedor ocupa al menos 65% del ancho y 65% del alto visibles de la ventana actual.
- Las señales deben estar dentro de ese mismo subárbol y tener superficie visible en pantalla.
- Al menos dos controles distintos; un mismo nodo no cuenta como dos controles.
- Cada señal suma una sola vez. Texto de controles debe pertenecer a un nodo accionable o a su descendiente;
  un ID exacto de control también cuenta. No se buscan palabras sueltas dentro de captions.
- Se normalizan mayúsculas y acentos. Se admiten etiquetas exactas o seguidas de puntuación/cantidades.
- `No me gusta`/`Dislike` no suman también Like.

### InstagramReelsDetector

Contenedores exactos (prefijo `com.instagram.android:id/`):
`clips_viewer_view_pager`, `clips_viewer_recycler_view`, `clips_viewer_root`, `clips_viewer_container`.

| Señal | Puntos | Etiquetas o IDs |
|---|---:|---|
| PLAYER_CONTAINER | 5 | Uno de los contenedores anteriores |
| REELS_CONTEXT | 2 | Reel, Reels; `clips_viewer_title` |
| LIKE_CONTROL | 1 | Like, Me gusta; `clips_viewer_like_button` |
| COMMENTS_CONTROL | 1 | Comment, Comments, Comentar, Comentarios; `clips_viewer_comment_button` |
| SHARE_CONTROL | 1 | Share, Compartir, Send, Enviar; `clips_viewer_share_button` |
| REEL_AUDIO | 2 | Audio, Original audio, Audio original; `clips_viewer_audio_attribution`, `clips_viewer_music_attribution` |

**Threshold: 9**, más al menos dos controles. Ejemplo: contenedor 5 + Audio 2 + Like 1 + Comment 1 = 9.
Like + Comment + Share no alcanzan ni siquiera con contenedor (8). Reels en navegación no dispara nada.

### YouTubeShortsDetector

Contenedores exactos (prefijo `com.google.android.youtube:id/`):
`reel_recycler`, `reel_watch_fragment_root`, `reel_watch_player`, `shorts_player_container`.

| Señal | Puntos | Etiquetas o IDs |
|---|---:|---|
| PLAYER_CONTAINER | 5 | Uno de los contenedores anteriores |
| SHORTS_CONTEXT | 2 | Shorts; `reel_player_shorts_logo` |
| LIKE_CONTROL | 1 | Like, Me gusta; `like_button`, `reel_like_button` |
| DISLIKE_CONTROL | 1 | Dislike, No me gusta; `dislike_button`, `reel_dislike_button` |
| COMMENTS_CONTROL | 1 | Comments, Comentarios, Comment; `reel_comment_button`, `comments_button` |
| SHARE_CONTROL | 1 | Share, Compartir; `reel_share_button`, `share_button` |
| REMIX_CONTEXT | 2 | Remix, Remixar, Remezclar; `reel_remix_button` |

**Threshold: 9**, más al menos dos controles. Contenedor + Shorts/Remix + dos controles = 9.
Contenedor + Like + Dislike + Comments + Share = 9 sin necesitar la etiqueta Shorts.
Los mismos controles en un video normal no bastan porque falta el contenedor.

Los pesos y vocabulario se editan en cada detector; los thresholds, geometría y requisitos comunes están
en `ScoringDetector.kt`. No reducir el threshold para compensar un ID desconocido: primero validar el árbol.

## Eventos, acción y seguridad del recorrido

Sólo `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED` y `TYPE_VIEW_SCROLLED`,
filtrados por los dos paquetes tanto en XML como en Kotlin. `flagReportViewIds` y
`canRetrieveWindowContent=true`; servicio protegido con `BIND_ACCESSIBILITY_SERVICE`.
No se pide `typeAllMask`, ventanas adicionales, capturas ni eventos de texto tecleado.

Se agrupan eventos con una lectura diferida de 120 ms (sin polling). Antes de analizar se verifica
el paquete de la ventana activa para descartar eventos atrasados. Si detector y preferencia lo permiten,
se ejecuta `GLOBAL_ACTION_BACK`. Cooldown global de 1200 ms con reloj monotónico, marcado antes de
intentar BACK, también si falla. Los eventos del propio BACK no disparan un bucle inmediato.
El Toast se muestra sólo si Android acepta la acción: «Reels bloqueados por NoScroll» o
«Shorts bloqueados por NoScroll». Android puede aceptar BACK sin que la app salga inmediatamente;
NoScroll no fuerza cierre ni bloquea el paquete entero.

Límites: 450 nodos, profundidad 35, texto/description de 300 caracteres. Nulos, fallos o truncamiento
invalidan la detección. Cada nodo hijo se libera en finally en API 26–32; desde API 33 no hay pool.
El root se libera al finalizar y las tareas pendientes se cancelan al interrumpir/destruir el servicio.

## Diagnóstico local

```bash
/home/tomas/Android/Sdk/platform-tools/adb logcat -s NOSCROLL_TREE:D NOSCROLL_DETECTOR:D '*:S'
```

- `NOSCROLL_TREE`: className, viewIdResourceName, text, contentDescription, padre, profundidad,
  visibilidad y bounds. Máximo 120 nodos por volcado, uno cada 5 segundos, campos limitados a 160 caracteres.
- `NOSCROLL_DETECTOR`: paquete, tipo de evento, detector, score, threshold, señales, resultado,
  motivo y acción (`BACK`, `BACK_FAILED`, `NONE`, `DISABLED`, `COOLDOWN`).
  Como máximo cinco decisiones por segundo; una decisión idéntica cada dos segundos.
- `result=BLOCK` representa la clasificación; `action=BACK` confirma que se pidió Atrás con éxito.
  Un switch desactivado produce `action=DISABLED` aunque el detector reconozca contenido.
- El logger sólo ejecuta código en `BuildConfig.DEBUG`. No escribe archivos ni transmite información.
  Logcat DEBUG puede mostrar texto privado visible de esas dos apps; no se exporta automáticamente.

Sin permisos INTERNET ni SYSTEM_ALERT_WINDOW; sin analytics, VPN, root, OCR, foreground service,
telemetría ni APIs externas. Sólo se persisten los dos booleanos de preferencias.

## Prueba manual exacta

1. Instalar el APK, abrir NoScroll y habilitar manualmente su servicio. Confirmar ambos switches ON.
2. **Instagram Feed:** abrir Inicio y desplazar varias publicaciones normales, incluidas publicaciones
   con Me gusta, comentarios, compartir y audio. Abrir comentarios, perfil y mensajes.
   Debe permitir navegar sin BACK ni Toast aunque la pestaña Reels esté visible.
3. **Instagram Reels:** abrir la pestaña Reels y, por separado, un Reel desde un perfil y desde el feed.
   Debe salir del reproductor mediante Atrás y mostrar «Reels bloqueados por NoScroll» si se reconocen
   sus señales. Volver a intentarlo después de 1,2 s; no debe producir una cascada de Atrás.
4. **YouTube normal:** navegar Inicio y búsqueda con estantes de Shorts visibles. Abrir un video largo,
   probar reproducción vertical/horizontal, comentarios, compartir y minirreproductor.
   Todo debe seguir utilizable, sin BACK ni Toast.
5. **YouTube Shorts:** abrir Shorts desde la pestaña y desde un resultado/estante.
   Debe salir con Atrás y mostrar «Shorts bloqueados por NoScroll» si se reconocen sus señales.
6. Apagar sólo Instagram Reels en NoScroll: Reels debe quedar permitido y Shorts seguir protegido.
   Invertir los switches y comprobar el comportamiento contrario.
7. Cerrar y volver a abrir NoScroll y reiniciar el teléfono: verificar que los switches se conservan.
   Comprobar el servicio tras reiniciar (su ciclo de vida lo controla Android).
8. Desactivar el servicio desde Ajustes y regresar: debe aparecer «⚠ Protección desactivada».
9. Repetir las pruebas con idioma español e inglés cuando sea posible.

Si no bloquea, observar `reason=NO_PLAYER_CONTAINER`, `INCOMPLETE_TREE` o `INSUFFICIENT_SIGNALS`
y contrastar los IDs con Logcat. No se deben ampliar las reglas sin repetir primero los casos negativos.

## Referencias de configuración

- [Compatibilidad oficial AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Plugin del compilador Compose](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [AccessibilityNodeInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)
