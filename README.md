## BookR

A minimalist, offline-first Android ebook reader with a monochrome Glassmorphism interface, customizable themes, persistent bookmarks, and reading notes.

## Features

- **Multiple ebook formats** — native support for FB2, FB2.ZIP, EPUB, PDF, TXT, HTML/XHTML, RTF, and MOBI/AZW3 (with PalmDOC LZ77 decompression).
- **Online Books Mode ("Онлайн книги")** — browse, search, read online, and download free books from **Project Gutenberg (Gutendex API)**, **OpenLibrary / Internet Archive**, curated Russian & World classics, and any **OPDS 1.2 Atom XML** catalog (including custom user-added OPDS servers).
- **Manga & Webtoon Section ("Манга")** — dedicated online & offline manga catalog (**MangaDex API v5** + built-in titles), chapter language filters (`RU` / `EN`), single & batch chapter downloading for offline reading, and page-level manga bookmarks.
- **Dedicated Manga Reader** — supports **Webtoon (Vertical Scroll)**, **Left-to-Right (LTR)**, and **Japanese Right-to-Left (RTL)** reading modes, pinch-to-zoom & double-tap zoom, eye-care/high-contrast/AMOLED-invert visual filters, and **Data Saver** mode.
- **Folder-based library** — import entire folders and index supported books, including subdirectories.
- **Advanced reading experience** — paginated reading, continuous scrolling, adjustable typography, in-book full-text search, and reading progress restoration.
- **Bookmarks, quotes, and manga bookmarks** — save passages, manga pages, and book metadata independently of the original files, with full JSON backup and restore.
- **Reading notes** — create, edit, search, and organize notes linked to books or selected passages.
- **Custom themes** — create and save themes with custom HEX/RGBA colors, transparency, and interface styling.
- **Custom backgrounds** — use personal images or animated GIFs with adjustable brightness, blur, opacity, and scaling.
- **Library management** — search, sorting, favorites, collections (shelves), and reading history.
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
