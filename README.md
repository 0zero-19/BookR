## BookR

A minimalist, offline-first Android ebook reader with a monochrome Glassmorphism interface, customizable themes, persistent bookmarks, and reading notes.

## Features

- **Multiple ebook formats** — support for FB2, FB2.ZIP, EPUB, PDF, TXT, HTML, and other formats as implemented.
- **Folder-based library** — import entire folders and index supported books, including subdirectories.
- **Advanced reading experience** — paginated reading, continuous scrolling, adjustable typography, and reading progress restoration.
- **Bookmarks and quotes** — save passages, preserve book metadata, and access bookmarks independently of the original files.
- **Reading notes** — create, edit, search, and organize notes linked to books or selected passages.
- **Custom themes** — create and save themes with custom HEX/RGBA colors, transparency, and interface styling.
- **Custom backgrounds** — use personal images or animated GIFs with adjustable brightness, blur, opacity, and scaling.
- **Library management** — search, sorting, favorites, collections, and reading history.
- **Offline-first** — reading and personal data are stored locally.
- **Privacy-focused** — no advertising or unnecessary tracking.

> Feature availability depends on the current implementation. Consult the release notes for verified format support and known limitations.

## Design

A monochrome interface built around black, white, and gray tones, transparent surfaces, subtle borders, and customizable Glassmorphism effects.

## Technology

- Java
- Android SDK
- Android Studio
- Gradle
- Local storage and database technologies used by the project

## Getting Started

1. Clone this repository.
2. Open the project in Android Studio.
3. Allow Gradle to sync and download the required dependencies.
4. Build and run the application on an Android device or emulator.

To build a debug APK from the project root on Windows:

```powershell
.\gradlew.bat assembleDebug
```

On Linux or macOS:

```bash
./gradlew assembleDebug
```

The debug APK is normally generated under:

`app/build/outputs/apk/debug/`

## Privacy

The application is designed for local reading and local storage of personal reading data. Review the actual permissions, dependencies, and network behavior of each release before making privacy claims.

## Contributing

Bug reports, feature suggestions, and contributions are welcome. Please include reproduction steps and relevant logs when reporting issues.
