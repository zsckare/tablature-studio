# Tablature Studio

**A native desktop tablature and sheet-music viewer with MIDI playback, built in Kotlin and Compose Multiplatform.**

Tablature Studio is an in-development application for guitarists, bassists, and musicians who want to open tablature files, follow notation while listening, and practice at their own pace. It runs locally, without a web browser, backend, or database.

> **Project status:** Active development / early-stage software. Import and notation support are evolving; complex scores and advanced Guitar Pro techniques may not be reproduced faithfully yet.

## Features

- **Combined notation and tablature:** conventional staff above guitar tablature, presented in a page-style score viewer.
- **Playback-following cursor:** a vertical playhead follows MIDI playback through the current measure.
- **Instrument tuning:** display the tuning associated with each imported track.
- **Multitrack playback:** select instruments and adjust mute, solo, and track volume.
- **Practice controls:** play, pause, resume, stop, seek, playback-speed adjustment, metronome, and A/B looping.
- **SoundFont support:** optionally load an `.sf2` soundbank, subject to the available JVM MIDI synthesizer.
- **MIDI export:** export the imported score as a MIDI file.
- **Zoom and navigation:** zoom the score and follow the current measure.
- **Offline-first:** no account, server, internet connection, or database required.

## Supported input formats

| Format | Extensions | Notes |
| --- | --- | --- |
| Guitar Pro 3–5 | `.gp3`, `.gp4`, `.gp5` | Legacy Guitar Pro binary import; support varies by file and technique. |
| Guitar Pro 7/8 | `.gp` | GPIF-based import from modern Guitar Pro archives. |
| MusicXML | `.musicxml`, `.xml` | Music notation interchange format. |
| ASCII tablature | `.txt`, `.tab` | Four- or six-string labeled tabs; rhythm is often unavailable in plain text. |

**Output:** Standard MIDI (`.mid`) export.

> Imported files are converted to Tablature Studio's internal music model. Some expressive techniques, articulations, layout instructions, and other format-specific metadata may be lost or simplified.

## Keyboard shortcuts

| Key | Action |
| --- | --- |
| `Space` | Play / pause / resume |
| `Esc` | Stop playback |

Keyboard shortcuts require the application workspace to have focus.

## Tech stack

- **Kotlin/JVM** — application and music processing
- **Compose Multiplatform Desktop** — native desktop UI
- **Java MIDI (`javax.sound.midi`)** — playback and MIDI export
- **Gradle Kotlin DSL** — builds and dependency management
- **JDK 17** — configured JVM toolchain

No WebView, JavaScript frontend, web server, or database is used.

## Project structure

```text
tablature-studio/
├── desktopApp/       # Compose Desktop UI and playback controls
├── music-core/       # Score, track, measure, beat, and note models
├── music-import/     # Guitar Pro, GPIF, MusicXML, and ASCII importers
├── music-renderer/   # Tablature layout calculations
├── music-playback/   # MIDI compilation and playback engine
├── gradle/           # Gradle wrapper files
├── build.gradle.kts
└── settings.gradle.kts
```

## Getting started

### Requirements

- JDK 17 (or a compatible JDK capable of providing the configured Java 17 toolchain)
- macOS or Windows desktop environment
- A working MIDI synthesizer on your system/JVM

### Run the application

**macOS / Linux shell:**

```bash
./gradlew :desktopApp:run
```

**Windows:**

```powershell
.\gradlew.bat :desktopApp:run
```

Open a supported tablature file using **Open file**. You can also load the included demonstration score using **Demo**.

### Compile and test

```bash
./gradlew :desktopApp:compileKotlin
./gradlew test
```

### Create desktop distributions

The Compose Desktop configuration includes DMG and MSI target formats. Build the distribution appropriate for your operating system:

```bash
./gradlew :desktopApp:packageDmg   # macOS
./gradlew :desktopApp:packageMsi   # Windows
```

Native packaging generally requires running on the target operating system.

## Current limitations

- The notation renderer is not yet a complete music-engraving engine; advanced rhythmic notation and complex score layouts may be incomplete.
- Modern and legacy Guitar Pro importers do not guarantee perfect fidelity for every version or technique.
- ASCII tabs do not reliably encode note durations.
- The playback cursor currently follows the measure timeline rather than every exact note attack.
- This is primarily a **viewer and practice tool**, not yet a full tablature authoring environment.

## Development goals

- Improve rhythmic engraving, rests, beams, ties, and accidentals.
- Increase Guitar Pro and MusicXML import fidelity.
- Improve note-accurate playback highlighting and navigation.
- Add richer keyboard shortcuts and accessibility support.
- Refine the native desktop experience while keeping the score viewer independent from the surrounding UI.

## License

No license is specified here. Add a `LICENSE` file when you decide how the project may be used and redistributed.
