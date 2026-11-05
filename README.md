# Tablature Studio

A **native, offline tablature reader and MIDI player** for macOS and Windows, built with **Kotlin/JVM + Compose Multiplatform Desktop**. No WebView, browser, JavaScript, backend, or database.

> **Development build, not a verified production release.** The Guitar Pro 5 reader is functional against the included synthetic regression fixtures and was checked using a real 244-measure, 11-track `.gp5` example. Desktop Compose was not built or run in the build environment; macOS and Windows installation, audio device compatibility, and the remaining file formats still need testing.

## Run locally

Requirements: JDK 17+, IntelliJ IDEA (or terminal), and network access for Gradle dependencies on the first build.

```bash
./gradlew :music-core:test :music-import:test :music-renderer:test :music-playback:test
./gradlew :desktopApp:run
```

Windows:

```powershell
.\gradlew.bat :desktopApp:run
```

If an audio synthesizer is unavailable, the UI should remain available while playback controls are disabled with an error message. Use a JDK with standard Java MIDI support, then optionally load an `.sf2` SoundFont from the UI.

## Working features

- Native dark UI with file picker, demo score, track selection, zoom, and virtualized score rendering.
- 4/6-string ASCII tablature reader (ASCII usually does **not** contain reliable rhythmic information).
- Basic MusicXML `score-partwise` support with fretted-instrument `technical/string` and `technical/fret` attributes.
- **Guitar Pro 5.00 / 5.10** decoding: title, artist, tuning, tracks, measures, beats, notes, voices, rhythms, selected musical techniques, tempo and MIDI instruments.
- Offline MIDI sequencing; guitar, bass, strings and drum channels; per-track mute, solo, volume.
- Play/pause/stop, seek, 25–200% tempo, 4-beat loop, arbitrary A/B loop, metronome and MIDI export.
- Optional SoundFont `.sf2` loading **if the installed Java synthesizer supports it**.

## Current unsupported or approximate behavior

- Guitar Pro **3/4** headers are recognized, but complete musical import is **not** implemented.
- Guitar Pro **6+** `.gpx`, modern `.gp` files and compressed MusicXML `.mxl` are not supported.
- The GP5 decoder consumes advanced effect structures (including bends, slides, harmonics, tremolo) but the MIDI synthesizer does **not** faithfully reproduce these guitar techniques. Dynamic variation, repeats, complicated endings, and some RSE features are also incomplete.
- Only tablature view is implemented: no full standard notation, auto line wrapping, note hit-testing, instrument recording, or score editing.
- The current song display highlights the active **measure**, not every exact note.
- The Java MIDI backend is not a guaranteed cross-platform SoundFont engine; FluidSynth is **not** integrated.
- Packaging configuration exists, but `.dmg` and `.msi` installers are not verified, signed, or notarized.

## Architecture

```text
desktopApp       Compose Desktop UI + local file picker
music-core       Score, Track, Measure, Beat, Note + timing model
music-import     GP5 decoder, MusicXML reader, ASCII reader
music-renderer   Compose-independent tab layout geometry
music-playback   MIDI compiler, JVM MIDI playback, SF2 opt-in
```

All imported data remains in memory; no database is created.

## Test evidence

- 23 core tests passed when compiled and executed with the Kotlin CLI toolchain, without Gradle/JUnit infrastructure.
- A real `.gp5` test file containing 244 measures and 11 tracks was decoded, and its sequence was exported to valid Type 1 MIDI and re-opened by the Java MIDI API.
- Build validation of Compose, Gradle integration tests, playback over real speakers, and native installers must be completed on macOS and Windows. See [TEST_PLAN.md](TEST_PLAN.md).

## Packaging

The package version is `1.0.0` **only because macOS requires a package major version greater than 0**; it is not an assertion of product maturity. The application source version remains `0.1.0-dev`.

```bash
./gradlew :desktopApp:packageDmg  # macOS only
./gradlew :desktopApp:packageMsi  # Windows only
```

## Attribution / technical references

The GP5 importer was implemented natively in Kotlin using the publicly documented GP5 binary structure for field layout and validation. Technical references:

- https://github.com/TadaoYamaoka/gp5_file_format
- https://pyguitarpro.readthedocs.io/en/stable/pyguitarpro/format.html

Review upstream license requirements for any code or resource you later incorporate. No upstream parser source has been included in this project.
