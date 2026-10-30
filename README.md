# Mi Launcher

Versión 0.4 · Diseño "OEM+" claro, pensado para un radio ligero (Android 11, ARMv7, 2 GB de RAM).

- Barra superior: nombre del coche, reloj grande y iconos de estado.
- Vista dividida: reproductor, tarjeta de Android Auto, vehículo (coche, velocidad, kilometraje) y mapa con clima.
- Dock inferior con 7 accesos: Radio, Música, Teléfono, Apps, Android Auto, Navegación y Ajustes.
- Reloj y fecha **reales** del sistema (se actualizan cada minuto con el aviso del propio Android).
- El dock abre apps reales (ver abajo). El clima es real (ver más abajo). Canción, velocidad y kilometraje siguen siendo **datos de prueba** de `data/Mock.kt`.
- Sin animaciones, sin imágenes: todo se dibuja con formas simples.

## 1. Obtener el APK (sin instalar nada)

El proyecto ya incluye el **Gradle Wrapper** (`gradlew`, `gradlew.bat`, `gradle/wrapper/`, Gradle 8.10.2) y el workflow
`.github/workflows/android.yml` ("Android CI"). GitHub compila solo cada vez que subes cambios.

**Con Git (recomendado), desde la carpeta del proyecto:**

    git add -A
    git commit -m "Actualizar launcher"
    git push

(Si es la primera vez: `git init`, `git branch -M main`, `git remote add origin https://github.com/TU_USUARIO/TU_REPO.git`
y luego `git push -u origin main`.)

Si Git no sube el jar del wrapper por una regla global de tu equipo: `git add -f gradle/wrapper/gradle-wrapper.jar`.
Si estás en Windows y `gradlew` pierde el permiso de ejecución: `git update-index --chmod=+x gradlew`.

**Sin Git:** en el repositorio, **Add file -> Upload files** y arrastra el contenido de la carpeta
(incluida la carpeta oculta `.github` y la carpeta `gradle`). **No arrastres `local.properties`.**

**Después:**
1. Pestaña **Actions** -> "Android CI". Tarda unos 5-8 minutos. Si no arranca solo: **Run workflow**.
2. Cuando salga el check verde, abre esa ejecución y descarga **mi-launcher-apk** (abajo, en *Artifacts*). El ZIP trae:
   - `release/app-release.apk` -> **el recomendado para el radio** (optimizado, va más fluido).
   - `debug/app-debug.apk` -> versión de respaldo, más lenta, por si la release da problemas.

Si sale una X roja, abre el paso que falló y envíame el texto del error.
Si en tu repositorio quedó un workflow viejo (`build.yml` u otro), bórralo: `git rm .github/workflows/build.yml`.

## 2. Instalar en el radio

Copia el APK al radio con una USB e instálalo desde el explorador de archivos, o por ADB:

    adb install -r app-release.apk

Para dejarlo como pantalla de inicio por defecto:

    adb shell cmd package set-home-activity com.mivehiculo.launcher/.MainActivity

Para volver al launcher original: desinstala la app, o

    adb shell pm clear-package-preferred-activities com.mivehiculo.launcher

## Qué abre cada botón

| Botón | Qué intenta, en orden |
|---|---|
| Ajustes | Ajustes del sistema -> apps de `Config.AJUSTES` |
| Teléfono | Marcador predeterminado -> apps de `Config.TELEFONO` |
| Música | App de música predeterminada -> apps de `Config.MUSICA`; si no hay, avisa con un mensaje |
| Navegación | Google Maps o Waze (`Config.NAVEGACION`) -> cualquier app de mapas |
| Apps | Lista de apps instaladas |
| Radio, Android Auto | Apps de `Config.RADIO` / `Config.ANDROID_AUTO` |

## Claves secretas (Jarvis y clima)

Las claves **no están en el código**: se inyectan al compilar y se leen como `BuildConfig.GROQ_API_KEY`
(Jarvis) y `BuildConfig.OPENWEATHER_API_KEY` (clima). Quien copie el proyecto no obtiene tus claves.

**Compilando en tu PC (Android Studio):** pon la clave en `local.properties`, en la raíz del proyecto:

    GROQ_API_KEY=gsk_tu_clave
    OPENWEATHER_API_KEY=tu_clave_del_clima

Ese archivo está en `.gitignore`: Git no lo sube. **Pero si subes archivos a GitHub arrastrándolos en el navegador,
no hay `.gitignore` que valga: NO arrastres `local.properties`.**

**Compilando en GitHub Actions:** como `local.properties` no se sube, crea un secreto: Settings -> Secrets and variables
-> Actions -> New repository secret, con el nombre `GROQ_API_KEY` (y `OPENWEATHER_API_KEY` si quieres clima).
Luego Actions -> Run workflow.

Sin clave la app funciona igual: Jarvis solo responde comandos locales y el clima muestra "--°C".

**Ojo:** la clave queda dentro del APK que compilas. No compartas ese APK (quien lo instale usaría tu clave).

## Clima real (OpenWeatherMap)

1. Crea una cuenta gratis en https://openweathermap.org y copia tu API key (menú de tu cuenta -> "API keys").
   Una clave nueva puede tardar hasta un par de horas en activarse.
2. Guárdala como secreto con el nombre `OPENWEATHER_API_KEY` (ver "Claves secretas" más abajo). **No se escribe en el código.**

Sin clave, sin permiso de ubicación, sin GPS o sin internet, el clima muestra `--°C` y la app sigue funcionando.

## Personalización de la pantalla principal

- **Bienvenida** (arriba a la izquierda, sobre la fecha): `Config.BIENVENIDA`.
- **Nombre del coche** (sobre la ilustración): `Config.VEHICULO`.
- **Ilustración del Hyundai Santa Fe 2007 celeste:** `CarIllustration` en `ui/Icons.kt`. Está dibujada con trazos
  (no es una imagen), cambia sola entre modo día y noche. El color de la carrocería es `cBody` (`0xFF8DB9DC`).

## Jarvis (asistente de voz)

Abre Jarvis con el icono del micrófono 🎤 del Dock. Es "presionar para hablar": no escucha en segundo plano.

1. **Consigue tu clave** gratis en https://console.groq.com/keys (empieza con `gsk_`) y guárdala como `GROQ_API_KEY`
   (ver "Claves secretas" más arriba). **No se escribe en el código.**
2. Toca el círculo grande, habla y vuelve a tocarlo (o espera: se detiene solo al callar).
3. Voz -> texto: `SpeechRecognizer` de Android. Respuesta en voz: `TextToSpeech` de Android.
4. **Comandos locales** (sin IA): "abre la música", "abre Google Maps", "abre Waze", "pon la radio", "abre el teléfono",
   "abre los ajustes", "abre Android Auto", "abre mantenimiento". Solo funcionan con frases cortas.

Requisitos del radio: la app **Google** (o cualquier servicio de reconocimiento de voz), voz sintética en español
(Ajustes > Idioma > Salida de texto a voz), internet, y que el micrófono del radio sea accesible para Android.
La música se pausa mientras hablas y se reanuda sola. Cambia el idioma con `IDIOMA_VOZ` en `Config.kt`.

## Reproductor de música (USB)

El botón **Música** del Dock abre nuestro reproductor local (Media3 / ExoPlayer). Busca música en cada USB que
aparezca en `/storage` y en las carpetas de `Config.RUTAS_MUSICA` (por defecto incluye `/storage/USB2/musica usb`).

- Formatos: MP3, M4A/AAC, FLAC, WAV, OGG, OPUS.
- Primero ves las carpetas (y "Todas las canciones"); toca una y luego una canción. La barra de progreso se puede tocar para saltar.
- La música sigue sonando al volver al Home. La tarjeta de música del Home y los botones del volante la controlan.
- La primera vez pide el permiso de almacenamiento. Si conectas el USB después, toca "Buscar de nuevo".
- "Abre la música" (Jarvis) también abre este reproductor.

## Radio FM

El botón **Radio** abre la app de Radio FM nativa del radio (no se puede rediseñar: la controla el chip).
La primera vez te deja elegirla de la lista; **mantén pulsado el botón Radio** para cambiarla.
Para volver al launcher: botón Atrás o Home.

## Cámara trasera (respaldo manual)

**Importante:** esta app NO toca la señal de video, ni la MCU, ni el cambio automático de cámara de tu radio.
Cuando metes reversa, el propio radio (hardware + firmware) sigue mostrando la cámara como siempre.

El botón **"Cámara trasera"** del Dock solo abre la app de cámara / AV-IN que instaló el fabricante
(usa el lanzador normal de Android de esa app: `packageManager.getLaunchIntentForPackage(paquete)`).

- **Primera vez:** se abre la lista de apps con el título "Elige la app de la cámara trasera". Toca la de tu radio
  (suele llamarse AV-IN, AVIN, Camera, Cámara trasera, Reverse...). Se guarda y desde entonces el botón la abre directo.
- **Cambiar la elección:** mantén pulsado el botón "Cámara trasera".
- **Si ya sabes el paquete:** ponlo en `Config.CAMARA` (tiene prioridad).
- No uses el botón mientras conduces.

### Buscar el nombre del paquete y la señal de reversa (por ADB, solo lectura)

    adb shell pm list packages | grep -i -E "avin|cam|rear|back|revers"

Para ver qué app está en pantalla cuando el radio muestra la cámara (pon reversa, o ábrela desde el menú del radio):

    adb shell dumpsys activity activities | grep -i -E "mResumedActivity|topResumedActivity"

Para ver si el firmware emite algún aviso al meter reversa, deja esto corriendo y mete/saca la reversa
(con el coche detenido y de forma segura):

    adb logcat | grep -i -E "revers|backcar|rear|camera"

Si aparece una acción de broadcast concreta (por ejemplo `...REVERSE...`) pásamela y evaluamos si es seguro escucharla.

## Modo Día / Modo Noche

- **Automático (por defecto):** es de noche si el sistema del radio lo indica (modo noche del sistema o `UiModeManager`),
  o si la hora local está entre las **18:30 y las 06:30**.
- **Manual:** en la pantalla de Mantenimiento (toca la tarjeta del coche en el Home) hay un selector arriba a la derecha:
  **Auto / Día / Noche**. Tu elección se guarda y sobrevive a reinicios. "Día" y "Noche" fuerzan el tema sin importar la hora.
- Prueba rápida: toca "Noche" y el launcher se oscurece al instante; "Día" lo devuelve. "Auto" vuelve a la detección.
- Por ADB puedes simular la señal del sistema (modo Auto): `adb shell cmd uimode night yes` / `adb shell cmd uimode night no`.

## Mantenimiento del vehículo

Desde el Home, **toca la tarjeta del centro (la del coche)**. Se abre la pantalla de Mantenimiento.
Para volver: flecha "←" arriba a la izquierda, o el botón Atrás.

- **Primer uso:** pulsa "Actualizar kilometraje" y escribe lo que marca tu tablero. Luego toca el recuadro
  "Último cambio" e ingresa el kilometraje de tu último cambio de aceite (la fecha queda como "no registrada").
- **Cada cambio nuevo:** "Hice el cambio hoy" guarda la fecha de hoy y el kilometraje actual.
- **Intervalo:** elige 3.000, 5.000, 7.500 o 10.000 km. Faltan = intervalo - (km actual - km del último cambio).
- Los datos viven en una base de datos SQLite (`maintenance.db`) dentro de la app: sobreviven a apagados y reinicios.
  Se pierden solo si desinstalas la app o borras sus datos. Para actualizar la app instala el APK nuevo encima
  (`adb install -r`), sin desinstalar.

## Música real: activar "Acceso a notificaciones"

La tarjeta de música lee lo que suena (título, artista, carátula, progreso) y controla Play/Pausa/Siguiente/Anterior.
Android exige un permiso especial para eso. Hay que activarlo UNA vez (y de nuevo si reinstalas la app):

1. Toca la tarjeta de música en la pantalla de inicio (mientras diga "Activa el acceso a notificaciones"):
   abre la pantalla de permisos. O ve a mano: **Ajustes > Apps y notificaciones > Acceso especial a apps >
   Acceso a notificaciones > Mi Launcher > Activar**.
2. Si tu radio esconde esa pantalla, por ADB (con el radio conectado):

       adb shell cmd notification allow_listener com.mivehiculo.launcher/com.mivehiculo.launcher.MediaListenerService

3. Abre Spotify (u otra app de música), pon una canción y vuelve al launcher.

Solo aparecen apps que publican su sesión de medios (Spotify, YouTube Music, VLC, Poweramp...).
Algunas apps de música o radio propias del fabricante no lo hacen y no se verán.

## 3. Nombres de paquete (para que los botones abran TUS apps)

Cada app de Android tiene un "nombre de paquete" (ej. `com.waze`). Los botones los usan para saber qué abrir.
Los de `Config.kt` son suposiciones. Para ver los reales de tu radio, por ADB:

    adb shell pm list packages

Busca los de radio, bluetooth y mapas, y ponlos en `Config.kt`
(`app/src/main/java/com/mivehiculo/launcher/Config.kt`).
Si un botón dice "app no encontrada", es que su nombre de paquete no coincide.

## Dónde cambiar cada cosa

| Quiero cambiar... | Archivo |
|---|---|
| Nombre del coche, qué app abre cada botón | `Config.kt` |
| Textos de prueba (canción, km...) | `data/Mock.kt` |
| Clave del clima | `Config.kt` |
| Colores (naranja/cobre, fondo, textos) | `ui/Theme.kt` |
| Distribución, tamaños, tarjetas, dock | `ui/HomeScreen.kt` |
| Iconos dibujados, coche y mapa | `ui/Icons.kt` |
| Lista de apps | `ui/AppDrawer.kt` |
