# Sprints 4–6 — estado actualizado

## Sprint 4: Guitar Pro
- ✅ Reconocimiento `.gp3`, `.gp4`, `.gp5` y metadatos.
- ✅ Decodificación GP5.00/GP5.10 en Kotlin: compases, afinaciones, pistas, voces, notas, tempo e instrumentos MIDI.
- ✅ Caso real verificado: `Buried Alive.gp5` (244 compases, 11 pistas).
- ✅ Pruebas GP5 sintéticas de versiones 5.00/5.10 y archivo truncado.
- ⏳ Decodificación musical GP3/GP4; casos especiales GP5, finales alternativos y efectos especiales.

## Sprint 5: MIDI
- ✅ Secuencia MIDI real con tiempo por compases, instrumentos y canal de batería.
- ✅ Reproducción, pausa, stop, seeking, velocidad, exportación a .mid, ligaduras básicas y voces simultáneas.
- ✅ Exportación MIDI del archivo real comprobada; 11 pistas, sin errores de serialización.
- ⏳ Afinar dinámicas, bends, slides y otras articulaciones. Validar sincronización con dispositivos reales.

## Sprint 6: SoundFont
- ✅ Opción para cargar SoundFont `.sf2` mediante `javax.sound.midi` donde sea compatible.
- ⏳ FluidSynth / sintetizador empaquetado estable para macOS y Windows.
- ⏳ Pruebas de rendimiento y calidad de sonido en hardware real.

**No se considera cerrado un sprint hasta que todas sus funciones prometidas hayan pasado las pruebas de aceptación.**
