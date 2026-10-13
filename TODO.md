# TODO

Snapshot of where the project stands and what is left. See `reqs.txt` for the
original requirements this plan implements.

## Status

| Module | State | Tests |
| --- | --- | --- |
| `:core` | M1 complete. Pure Kotlin, no Android, no I/O. | 11 |
| `:parser` | Not started. | 0 |
| `:data` | Not started. | 0 |
| `:app` | Scaffold only (MainActivity stub). | 0 |

Run everything: `./scripts/test.sh` (or `--core-only` before `:parser`/`:data`/`:app` need the Android SDK).

---

## M0 — Scaffold

- [x] `build-logic/convention` with `reader.android-application`, `reader.android-library`,
      `reader.kotlin-library`, `reader.detekt` convention plugins (AGP 9.3.1, minSdk 26,
      compileSdk/targetSdk 36, jvmToolchain 21).
- [x] `gradle/libs.versions.toml` version catalog.
- [x] Module skeletons: `:core`, `:parser`, `:data`, `:app`.
- [x] `config/detekt/detekt.yml`.
- [x] `scripts/{setup-dev-env,build,test,uninstall}.sh` ported, `APP_ID=dev.reader.app`.
- [x] Verified: full project configures and `:app` compiles against the local Android SDK.

## M1 — Core domain + RichText model (`:core`)

- [x] Ids (`BookId`, `ChapterId`, `CategoryId`, `BookmarkId`, `ThemePresetId`).
- [x] `Book`, `Chapter`, `Category`, `Bookmark`, `Progress`, `BookFormat`.
- [x] `ReaderTheme` (font, size, colour, bg colour, line/word/letter/paragraph spacing,
      justification) with Default/Sepia/Night built-in presets.
- [x] `AppTheme` (seed colour, light/dark/amoled/system).
- [x] `ReadingMode` (continuous scroll, paged vertical, paged horizontal LTR/RTL),
      `PdfRenderMode` (reflow text / fixed page, chosen per book).
- [x] `TapZoneConfig`/`TapAction` — configurable 3x3 tap grid, enable/disable.
- [x] `ReaderSettings` — selectable-text toggle, keep-screen-awake, book-styling/CSS toggle.
- [x] `RichDocument`/`Block`/`Span` — format-agnostic document model shared by every parser.
- [x] `NaturalSortComparator` — chapter ordering for light novels ("Chapter 2" < "Chapter 10").
- [x] `StatsCalculator` — total reading time, words read, average WPM, current streak.
- [x] Unit tests (11), detekt clean.

## M2 — Format parsers (`:parser`)

- [ ] Unified `ParsedBook` model (metadata + chapters as `RichDocument` or image pages).
- [ ] `TxtParser` — paragraphs split on blank lines.
- [ ] `MarkdownParser` (commonmark-java) — headings/lists/emphasis to `RichDocument`.
- [ ] `HtmlParser` (Jsoup) — reused by both standalone HTML files and EPUB chapters.
- [ ] `EpubParser` (`java.util.zip` + Jsoup over OPF/NCX) — metadata, cover, chapter order.
- [ ] `ComicArchiveParser` — cbz via zip; cbr via junrar, flagged experimental (licensing).
- [ ] `PdfTextParser` (pdfbox-android) — reflow-mode text extraction.
- [ ] `PdfPageRenderer` (`android.graphics.pdf.PdfRenderer`) — fixed-page mode; Android-only,
      weak Robolectric coverage, verify manually rather than by unit test.
- [ ] Per-format fixture files under `src/test/resources`, unit tests per parser.

## M3 — Data layer (`:data`)

- [ ] Room schema: `books`, `chapters`, `categories`, `book_categories`, `bookmarks`,
      `progress`, `reading_sessions` (append-only event log).
- [ ] FTS4 virtual table over chapter plain text for in-book search.
- [ ] DataStore (Okio + kotlinx-serialization JSON): settings, `ReaderSettings`,
      `ReaderTheme`/`AppTheme` presets, tap-zone config, category order.
- [ ] Repositories exposing Flows: library, progress, bookmarks, settings, stats.
- [ ] Folder scanning: SAF persisted-uri + `DocumentFile` tree walk, extension matching,
      upsert into Room. Light-novel "folder = book, files = chapters" import variant.
- [ ] Import/export bundle: metadata + progress + bookmarks + settings as JSON, optional
      zipped copies of source files.
- [ ] Robolectric + in-memory Room tests, Okio `FakeFileSystem` tests for DataStore.

## M4 — Reader rendering core (`:app`)

- [ ] Unified reflow renderer: continuous scroll / paged vertical / paged horizontal
      (LTR + RTL), driven by `ReaderTheme` + `ReaderSettings`.
- [ ] Tap-zone grid overlay with a preview mode showing the regions.
- [ ] Selectable-text toggle (disables `SelectionContainer` when off).
- [ ] Keep-screen-awake (never / while reading / always).
- [ ] Custom-CSS-subset mapping (font-family/colour/bold/italic/align/margin) +
      "ignore book styling" toggle. No WebView — full CSS fidelity is out of scope.
- [ ] Reading-progress indicator, persisted on scroll/page change.
- [ ] Separate Image Page pager (pinch-zoom, swipe) for PDF fixed-mode and comics.
- [ ] Golden-image render tests across theme variants (Robolectric `@GraphicsMode(NATIVE)`,
      reusing daily-checklist's pixel-diff technique).

## M5 — Library & book management (`:app`)

- [ ] Shosetsu-style library grid/list: cover, title, progress bar.
- [ ] Swipeable category tabs — add/rename/reorder, default "All" category.
- [ ] Add books: single file, multiple files, folder picker (auto-discovery), light-novel
      folder import (one folder = one book, files = chapters).
- [ ] Book detail screen: chapter list with progress, bookmarks.
- [ ] Edit metadata overrides (title/author/cover) without touching the source file.

## M6 — Search, bookmarks, appearance editor (`:app`)

- [ ] Global search: library metadata + in-book FTS text search.
- [ ] Bookmark create/list/jump-to.
- [ ] Appearance screen: app theme (seed colour, light/dark/amoled), reader theme editor
      (font/size/colour/bg/spacing/justification) saved as named presets.
- [ ] Tap-zone editor with live preview overlay.

## M7 — Import/export UI + analytics (`:app`)

- [ ] Export/import screens for the M3 bundle format.
- [ ] Analytics/Stats page: books finished, total reading time, total words read,
      average WPM, streaks — replayed from the `reading_sessions` log.

## Future (do not do unless specifically told)

- Text-to-speech read-aloud.
- Tap-word dictionary/translate lookup.
- Auto-scroll with adjustable speed.
- Reading goals/streak reminders.
- Cloud backup.

## Notes

- Package `dev.reader`; modules `:core` (pure JVM), `:parser`/`:data` (Android libraries —
  pdfbox-android/junrar/Room need Android), `:app` (Android application).
- Android SDK is expected at `$ANDROID_SDK_ROOT`/`~/Android/sdk`; `local.properties` points there.
- KSP version is independent of the Kotlin version number since KSP2 — pin both separately
  in `gradle/libs.versions.toml` rather than assuming a matching prefix.
