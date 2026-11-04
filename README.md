# Tablature Studio

Native offline tablature reader for macOS and Windows. **Kotlin/JVM + Compose Multiplatform Desktop**; no WebView, JavaScript, backend or database.

## Requirements
- JDK 17+
- Gradle 8.10+ (or generate a Gradle wrapper locally)
- macOS or Windows for native packaging

## Run
```bash
gradle :desktopApp:run
```

## Test
```bash
gradle test
```

## Current scope (Sprints 1–3)
- Multi-module desktop foundation and dark UI
- Immutable score domain and validation
- Built-in demo with two tracks
- Native Canvas drawing of strings, bars and fret numbers
- Track selection, horizontal scroll and zoom
- Local file picker for 4/6-string ASCII TAB (`.txt`, `.tab`)
- Explicit errors for unsupported files; MusicXML importer is **not implemented yet**

## Known limitations
- ASCII TAB has no reliable rhythm metadata, so note durations remain unknown.
- No audio playback until Sprint 5.
- No drag-and-drop or notation symbols yet.
- Canvas currently displays one continuous system; line wrapping and hit testing remain future improvements.
- Guitar Pro formats are not supported until later sprints.
- Build and native runtime have not been verified in this environment; Gradle is not installed here.

## Architecture
- `music-core`: immutable music model, validation and demo score
- `music-import`: format importer interface and ASCII TAB importer
- `music-renderer`: UI-independent geometry layout
- `music-playback`: interface boundary for future audio
- `desktopApp`: native Compose UI and file dialog
