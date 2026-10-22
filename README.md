# EMI Launcher

Launcher minimalista para Android TV, pensado para un Xiaomi TV de 32" con
hardware limitado (1–1,5 GB de RAM). Prioridad absoluta: arranque instantáneo
y APK mínimo.

- Kotlin con Views clásicas (sin Jetpack Compose, sin Leanback, **sin ninguna
  dependencia externa**: solo el framework de Android y la stdlib de Kotlin).
- Una sola Activity, sin animaciones, sin red.
- `minSdk 28`, `targetSdk 34` (AGP 8.1.3 / Gradle 8.4, compatible con
  Android Studio Giraffe/Hedgehog 2023 o más nuevo).
- Navegación 100 % con D-pad: foco visible (borde blanco grueso), orden lógico
  izquierda→derecha / arriba→abajo, el botón **PlayStation** tiene el foco al
  iniciar.
- Cuadrícula 2×3: PlayStation (HDMI 1, destacado), Netflix, Prime Video,
  YouTube, HBO Max y Ajustes. Las apps no instaladas se ocultan solas.
- Las apps se abren con `getLeanbackLaunchIntentForPackage`, con fallback a
  `getLaunchIntentForPackage`.

## Botón PlayStation (HDMI 1)

1. Lista las entradas *passthrough* con `TvInputManager` e intenta identificar
   la HDMI 1 por etiqueta/id (`HDMI 1`, `HDMI1`, `HDMI_1`).
2. La abre con `ACTION_VIEW` sobre
   `TvContract.buildChannelUriForPassthroughInput(inputId)`.
3. Si no puede identificarla con seguridad (en Xiaomi los ids varían), muestra
   una lista de entradas para elegir y guarda la elección en
   `SharedPreferences`.
4. **Pulsación larga** sobre el botón: olvida la elección y vuelve a mostrar la
   lista.
5. Si nada funciona: abre la app de TV en vivo del sistema y, como último
   recurso, los Ajustes.

## Compilar

Requisitos: JDK 17+ y Android SDK (define `ANDROID_HOME` o crea
`local.properties` con `sdk.dir=/ruta/al/sdk`). La primera compilación descarga
Gradle y el Android Gradle Plugin.

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Para un APK aún más chico (minificado con R8):

```bash
./gradlew assembleRelease
```

(El release requiere firma; para uso personal basta el debug.)

## Instalar por ADB en red

En el TV: **Ajustes → Preferencias del dispositivo → Acerca de** → pulsar
varias veces sobre "Compilación" para habilitar opciones de desarrollador, y
activar **Depuración por USB/ADB** (en algunos Xiaomi aparece como "Depuración
inalámbrica" o hay que activar también *Seguridad → Fuentes desconocidas*).

```bash
adb connect IP_DEL_TV:5555     # la IP aparece en Ajustes → Red
adb install app/build/outputs/apk/debug/app-debug.apk
```

Para actualizar una versión ya instalada: `adb install -r ...`.

## Confirmar los nombres de paquete de las apps

Los paquetes pueden variar según la región/versión. Para listarlos:

```bash
adb shell pm list packages | grep -i netflix
adb shell pm list packages | grep -i amazon
adb shell pm list packages | grep -i youtube
adb shell pm list packages | grep -iE "hbo|wbd|max"
```

Los que usa el launcher (edita `MainActivity.kt` y el bloque `<queries>` del
manifest si en tu TV difieren):

| App | Paquetes que prueba, en orden |
| --- | --- |
| Netflix | `com.netflix.ninja` |
| Prime Video | `com.amazon.amazonvideo.livingroom`, `com.amazon.avod.thirdpartyclient` |
| YouTube | `com.google.android.youtube.tv` |
| HBO Max | `com.wbd.stream`, `com.hbo.hbonow`, `com.hbomax.hbomax` |

## Usarlo como launcher predeterminado

Tras instalarlo, al pulsar HOME Android debería preguntar qué launcher usar.
En los Xiaomi con PatchWall normalmente **no pregunta**: PatchWall queda fijo
como HOME y hay que desactivarlo.

> ⚠️ **Primero comprueba que EMI Launcher funciona**: ábrelo desde la lista de
> apps, verifica que lanza Netflix/YouTube/HDMI, y recién entonces desactiva
> PatchWall. Si desactivas PatchWall con este launcher roto, el TV puede quedar
> sin pantalla de inicio (se recupera igual por ADB con el comando de revertir).

Identifica el paquete del launcher de Xiaomi (suele ser `com.mitv.tvhome`):

```bash
adb shell pm list packages | grep -i mitv
```

Desactivarlo (no lo desinstala, solo lo apaga para el usuario actual):

```bash
adb shell pm disable-user --user 0 com.mitv.tvhome
```

Pulsa HOME: debería abrirse EMI Launcher.

**REVERTIR** (volver a PatchWall):

```bash
adb shell pm enable com.mitv.tvhome
```

Si algo sale muy mal y no hay launcher, ese mismo `pm enable` por ADB restaura
PatchWall; EMI Launcher también puede abrirse directo con:

```bash
adb shell am start com.emi.launcher/.MainActivity
```
