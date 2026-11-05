# Estado verificable — Tablature Studio

## Implementado en esta entrega
- Importador MusicXML partwise básico para instrumentos con tablatura explícita (`notations/technical/string` y `fret`).
- Lectura de compases, divisiones, duraciones, acordes, silencios, compás, afinaciones y nombres de pistas.
- Protección frente a entidades XML externas (XXE), límite de tamaño y errores explícitos para voces polifónicas no soportadas.
- Pruebas unitarias de lectura de notas y bloqueo de entidades externas.

## Validación
- `kotlinc` compiló `music-core` y `music-import` sin errores.
- Gradle no pudo ejecutarse: la distribución Gradle 9.6.0 requiere descarga de red, bloqueada en el entorno.

## Pendiente para versión estable
- Parser completo GP3/GP4/GP5 (actualmente solo cabecera/metadatos).
- GPX/GP modernos y MusicXML complejo (voces múltiples, ritmos avanzados).
- Síntesis SoundFont multiplataforma garantizada y técnicas musicales avanzadas.
- Mezclador de volumen por pista, cursor sincronizado, metrónomo y loop A/B arbitrario.
- Pruebas integrales en macOS y Windows, firma/notarización y verificación de instaladores.

No se declara terminada la aplicación.
