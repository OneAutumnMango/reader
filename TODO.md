# TODO

Snapshot of where the project stands and what is left. See `reqs.txt` for the
original requirements this plan implements.

## Status

| Module | State | Tests |
| --- | --- | --- |
| `:core` | M1 complete. Pure Kotlin, no Android, no I/O. | 13 |
| `:parser` | M2 complete. | 6 |
| `:data` | M3, M5 importer, M7 export/import complete. | 13 |
| `:app` | M4, M5, M6, M7 complete. | 35 |

67 tests total. Run everything: `./scripts/test.sh --lint`.

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

- [x] Unified `ParsedBook`/`ParsedChapter` model (text chapters as `RichDocument`, image
      chapters as an ordered page list).
- [x] `TxtParser` — paragraphs split on blank lines.
- [x] `MarkdownParser` (commonmark-java) — renders to HTML, reuses the HTML converter.
- [x] `HtmlParser` (Jsoup) + shared `parseHtmlToRichDocument` — reused by Markdown and EPUB too.
- [x] `EpubParser` (`java.util.zip` + Jsoup XML parser over container.xml/OPF) — metadata,
      cover (`<meta name=cover>` or `properties=cover-image`), spine-ordered chapters.
- [x] `ComicArchiveParser` — cbz via `java.util.zip`; cbr via junrar. cbr has no automated
      fixture test (no tooling to author a `.rar` in this environment) — cbr stays experimental
      pending a real-world fixture.
- [x] `PdfTextParser` (pdfbox-android, reflow mode) — text extraction, paragraph-split like Txt.
- [x] `PdfPageRenderer` (`android.graphics.pdf.PdfRenderer`, fixed-page mode) — implemented,
      not covered by an automated test (PdfRenderer needs a real FD/surface); verify manually.
- [x] Unit tests: Txt/Html/Markdown/Epub/Cbz run as plain JVM tests; Pdf runs under Robolectric
      (round-trips a pdfbox-android-generated PDF — confirms the library works under Robolectric).

## M3 — Data layer (`:data`)

- [x] Room schema: `books`, `chapters`, `categories`, `book_categories`, `bookmarks`,
      `progress`, `reading_sessions` (append-only event log).
- [x] FTS4 virtual table (`chapter_fts`) over chapter plain text for in-book search.
- [x] DataStore (Okio + kotlinx-serialization JSON, `ReaderStores`/`JsonSerializer`/
      `PreferencesFile` mirroring daily-checklist's store pattern): `AppTheme`,
      `ReaderSettings`, `ReaderTheme` presets + active preset name.
- [x] Repositories exposing Flows: `LibraryRepository`, `ProgressRepository`,
      `BookmarkRepository`, `StatsRepository`, `SearchRepository`, `SettingsRepository`.
- [x] Entity <-> core model mappers (`BookMappers.kt`, `ProgressMappers.kt`).
- [x] Robolectric in-memory Room tests (library/progress/bookmarks/stats/search) + Okio
      `FakeFileSystem` DataStore tests (restart survival, corrupt file, unknown fields). 10 tests.
- [ ] Folder scanning: SAF persisted-uri + `DocumentFile` tree walk, extension matching,
      upsert into Room. Light-novel "folder = book, files = chapters" import variant.
- [ ] Import/export bundle: metadata + progress + bookmarks + settings as JSON, optional
      zipped copies of source files.

**Deferred to a later pass**: folder auto-discovery scanning and the import/export bundle
need `:app`-level file-picker/SAF plumbing to be useful, so they move with M5/M7 instead
of blocking the rest of M3.

## M4 — Reader rendering core (`:app`)

- [x] Unified reflow renderer: `ContinuousReader` (LazyColumn) / `PagedReader`
      (Horizontal/VerticalPager, LTR + RTL via `LocalLayoutDirection`), driven by
      `ReaderTheme` + `ReaderSettings`. Pagination is block-chunked (`paginate()`), an
      approximation of true measured pagination — see open question below.
- [x] Tap-zone grid overlay (`TapZoneOverlay`) with a preview/label mode showing the regions.
- [x] Selectable-text toggle (`SelectionContainer`/`DisableSelection` in `ContinuousReader`).
- [x] Keep-screen-awake (`KeepScreenAwakeEffect`, never / while reading / always).
- [x] Reading-progress indicator plumbing: `PagedReader`/`ImagePager` report `onPageChanged`;
      wiring that into `ProgressRepository` is a `:app` integration task left for M5.
- [x] `ImagePager` (pinch-zoom not yet added) for PDF fixed-mode and comics — page content is
      supplied by the caller since bitmap loading differs per source (M5 wires this to Coil).
- [ ] Custom-CSS-subset mapping + "ignore book styling" toggle — deferred to M6 alongside the
      appearance editor, since it needs a place in the UI to expose the toggle.
- [x] Compose UI tests (Robolectric + `createComposeRule`): continuous rendering, tap-zone hit
      testing, keep-awake effect, pagination grouping. 8 tests.

**Simplified from the original plan**: true pixel-measured pagination and golden-image
screenshot tests were scoped down to block-count pagination and semantics-tree assertions,
to keep this milestone to a reasonable size — see "Further considerations" at the end of
this file.

## M5 — Library & book management (`:app`)

- [x] `BookImporter` (`:data`): single-file import (txt/md/html/epub/cbz/cbr/pdf) and
      light-novel folder import (one folder = one book, each file = one chapter,
      naturally sorted). Generates `Book`/`Chapter` and saves via `LibraryRepository`.
- [x] Shosetsu-style library grid: `BookCard`/`BookCover` (cover image via Coil, or a
      lettered placeholder), progress bar when in progress.
- [x] Swipeable category tabs (`CategoryTabs`, `PrimaryScrollableTabRow`) — "All" plus
      one tab per `Category`; `LibraryScreen` filters books by the selected category.
- [x] Book detail screen (`BookDetailScreen`): chapter list with click-through, bookmark count.
- [x] 7 Compose UI tests (card click/content, category filtering, chapter click) +
      2 `BookImporter` Robolectric tests. detekt clean.
- [ ] Add-book UI (file/folder pickers via SAF `ActivityResultContracts`) — the import
      *logic* exists (`BookImporter`), but the picker UI itself needs a real Activity and
      is best verified manually/on-device; not meaningfully testable under Robolectric.
- [ ] Folder auto-discovery scanning (watch a folder, re-scan on demand) — still deferred,
      now to M7 alongside import/export, since both need the same SAF plumbing.
- [ ] Edit metadata overrides (title/author/cover) without touching the source file —
      deferred to M6 alongside the appearance editor's dialog patterns.

## M6 — Search, bookmarks, appearance editor (`:app`)

- [x] Library search: `filterBooksByQuery` (title/author, case-insensitive) + `SearchScreen`.
      In-book FTS search already exists in `:data` (`SearchRepository`, M3) — wiring the two
      together behind one search bar is a small `:app` integration task left open.
- [x] `BookmarksScreen`: jump-to and delete actions per bookmark.
- [x] `ReaderThemeEditor`: font family field, font/line/word/letter/paragraph spacing sliders,
      justification picker — edits a `ReaderTheme` live.
- [x] `ReaderSettingsEditor`: reading-mode picker, selectable-text toggle, keep-screen-awake
      picker, and the "use book styling (CSS)" toggle deferred from M4.
- [x] `TapZoneEditor`: tap a cell to cycle its action, grid always labelled (acts as its own preview).
- [ ] App-wide `AppTheme` (seed colour palette, light/dark/amoled) editor — `AppTheme` model
      exists (M1) but has no editor UI yet; deferred, since it needs a palette-generation
      helper (daily-checklist's `PaletteFactory` equivalent) that hasn't been ported.
- [ ] Named theme-preset save/load UI (the `PreferencesFile.themePresets` list exists in `:data`
      since M3; a picker/save-as UI for it is still open).
- [x] 15 Compose UI tests (search, bookmarks, theme/settings/tap-zone editors) + 2 pure-JVM
      search-filter tests. detekt clean.

**Gotcha hit while testing**: a `Slider` placed above other clickable rows in the same `Column`
can silently swallow clicks on those rows under Robolectric — fixed by ordering pickers/switches
before sliders. Recorded in repo memory for next time.

## M7 — Import/export UI + analytics (`:app`)

- [x] `ExportBundle`/`ExportRepository` (`:data`): serializes every book, chapter, category,
      bookmark, progress row and the preferences file to one JSON blob; `import()` restores
      all of it into a (possibly fresh) database + settings store. Round-trip tested.
- [x] `ImportExportScreen` (`:app`): export/import buttons + status message; the actual
      file-save/open picker (SAF `ActivityResultContracts.CreateDocument`/`OpenDocument`) is
      the caller's job and isn't meaningfully testable under Robolectric — same reasoning as
      the M5 add-book pickers.
- [x] `StatsScreen`: books finished, total reading time (`Duration.toReadableDuration()`,
      e.g. "2h 15m"), total words read, average WPM, current streak — replayed from
      `StatsRepository`/`StatsCalculator` (M1/M3).
- [x] 7 tests (duration formatting, stats display, import/export button wiring), detekt clean.

With M7 done, every milestone in this file that doesn't depend on a real file picker or a
ported palette-generation helper is complete. See "Open questions for review" below for
what's left and why.

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

## Open questions for review

- **No navigation graph / app entry point yet**: every screen built in M4–M7 is a standalone,
  independently-testable composable (`ReaderScreen`, `LibraryScreen`, `BookDetailScreen`,
  `SearchScreen`, `BookmarksScreen`, appearance editors, `StatsScreen`, `ImportExportScreen`).
  `MainActivity` still only shows the M0 placeholder — nothing wires these screens together,
  threads real `LibraryRepository`/`ProgressRepository`/`SettingsRepository` instances through
  them, or lets you navigate library → book detail → reader. This is the single biggest
  remaining gap before the app is actually usable on a device.
- **Add-book, folder-auto-discovery, and export/import file pickers**: `BookImporter` and
  `ExportRepository` work against plain `java.io.File`s and are fully tested; the SAF
  (`ActivityResultContracts`) UI to actually pick a file/folder/export destination doesn't
  exist yet and can't be meaningfully exercised under Robolectric. Needs on-device
  verification once wired up.
- **App-wide `AppTheme` (seed-colour palette, light/dark/amoled) has no editor UI** —
  the model exists (M1), but a picker needs a palette-generation helper equivalent to
  daily-checklist's `PaletteFactory`, which was never ported. Named `ReaderTheme` preset
  save/load UI is similarly missing (the data model and storage exist; no picker).
- **Custom CSS subset**: `ReaderSettings.customCss`/`useBookStyling` exist and the editor
  toggle is wired (M6), but nothing in the reflow renderer actually *applies* a parsed CSS
  subset yet — EPUB/HTML styling beyond bold/italic/headings/lists isn't mapped at all.
- **requirement 21** (added to `reqs.txt` after planning started): "progress tracker while
  reading, like page 14/100, 14%, chapter X" — the plumbing exists (`onPageChanged` callback,
  `ProgressRepository`), but there's no on-screen indicator widget, pending the navigation
  graph above.
- **cbr (RAR comics)**: implemented via junrar, but has no automated test — no tool in this
  environment can author a `.rar` fixture. Confirm you're fine with junrar's license, or say the
  word to drop cbr support and keep cbz only.
- **Pagination accuracy (M4)**: paged reading modes currently group a fixed number of blocks
  per page rather than measuring actual text layout against screen size/font. Pages will not
  land on a clean boundary the way ReadEra/Moon+ Reader do.
- **Golden-image screenshot testing (M4)**: the plan originally called for daily-checklist's
  pixel-diff golden-image technique to verify reader themes visually. This pass used
  semantics-tree assertions (`onNodeWithText`, tap-zone hit testing) instead, which verify
  behaviour but not actual visual appearance (colours, spacing, fonts rendering correctly on
  screen). Flag if you want the golden-image approach added for appearance verification.

