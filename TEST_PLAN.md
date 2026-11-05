# Plan de pruebas para IntelliJ IDEA — Tablature Studio

## 1. Compilación

```bash
./gradlew clean :music-core:test :music-import:test :music-renderer:test :music-playback:test
./gradlew :desktopApp:run
```

JDK 17+ y Gradle Wrapper incluido. Si falla el primer comando, conserva la salida completa de Gradle.

## 2. Pruebas funcionales en macOS y Windows

1. Abre `Buried Alive.gp5` desde **Open file**. El título deberá ser *Buried Alive* y deberán aparecer 11 pistas, incluidas *Clean Guitar*, *Bass* y *Drums*.
2. Selecciona *Clean Guitar*: deben aparecer los números de traste. Haz zoom y comprueba desplazamiento por compases.
3. Reproduce con **Play**. Verifica el sonido, el progreso, la barra de navegación y que se ilumina el compás actual.
4. Prueba **Pause** y **Stop**, y verifica que no quedan notas sostenidas.
5. Activa **Mute**, **Solo** y modifica el volumen en dos pistas distintas.
6. Prueba velocidad 25–200%, **Set A**, **Set B / Loop**, **Clear loop** y metrónomo.
7. Elige **Export MIDI**, guarda `.mid` y ábrelo con otro programa.
8. Carga un `.sf2` propio y verifica si el sintetizador JVM lo acepta. Si responde que no, no es necesariamente un error del archivo.
9. Abre una tablatura ASCII de 4/6 cuerdas y un MusicXML sencillo con datos de cuerda/traste.
10. Abre un GP5 truncado y comprueba que aparece un error, no una pantalla con datos inventados.

## 3. Instaladores

En macOS:

```bash
./gradlew :desktopApp:packageDmg
```

En Windows:

```powershell
.\gradlew.bat :desktopApp:packageMsi
```

Los instaladores deben validarse en cada sistema. El versionado técnico de DMG/MSI (`1.0.0`) no indica estabilidad del software.

## 4. Limitaciones que siguen pendientes

- Archivos GP3/GP4 aún no tienen importador musical completo.
- GPX/GP moderno todavía no está soportado.
- Las técnicas avanzadas GP5 pueden estar dibujadas o reproducidas de forma aproximada.
- Renderizado de notación estándar, audio SoundFont garantizado y distribución firmada todavía pendientes.
- No hay backend, WebView ni base de datos.
