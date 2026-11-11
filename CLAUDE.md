# Media Sorter

Kotlin/Spring Boot CLI (Picocli) that sorts photos and videos from a flat input directory into a
date-based directory tree in an output directory. Originally built for iPhone exports, also used for
camera files.

Stack: Java 25, Kotlin 2.4, Spring Boot 4.1, Gradle 9.8, `com.drewnoakes:metadata-extractor` for
metadata. Versions live in `gradle.properties`.

## Functionality

Entry: `MediaSorterCommand` → `logic/MediaSorterLogic.kt` (all logic is in this one class).

Sort mode (`-i <input> -o <output> [-t]`):
1. Delete `*.AAE` files (iPhone edit sidecars) in the input directory.
2. For every photo (`JPG`, `JPEG`, `HEIC`, `PNG`) and video (`MP4`, `MOV`) in the input directory
   (not recursive, macOS `._*` files are skipped):
   - Determine the creation date (see below).
   - Move it to `<output>/yyyy/yyyyMM/yyyyMMdd/<filename>`.
   - `HEIC` files are converted to JPG first via ImageMagick (`magick mogrify -format JPG`, must be on
     the PATH); the HEIC original is deleted after a successful conversion.
3. `-t` / `--test` = simulation: only logs what would happen (no delete, move, mkdir or conversion).

Examine mode (`-e <file>`): logs all metadata directories/tags of one file plus the resolved date.
Use it to debug files with missing or wrong dates.

### Creation date resolution (`getCreationDate`)
- Photos: EXIF IFD0 `DateTime`, then EXIF SubIFD `DateTimeOriginal`.
- Videos: `com.apple.quicktime.creationdate` (QuickTimeMetadataDirectory, iPhone; `+1h` is applied),
  then the movie header `Creation Time` (mvhd atom, UTC, written by most non-Apple cameras):
  first from `QuickTimeDirectory` (brand `qt`), then from `Mp4Directory` (MP4 brands like `mp42`,
  also seen with a `.MOV` extension). Each source has its own `getMetadata…Date` function in the
  chain; header values before 1971 are treated as unset.
- Fallback: file system date (earlier of creation/last-modified, `+1h`), logged as
  `*** No meta data entry: <file>`.
- The `+1h` corrections are historical hacks; keep them in mind when dates are off by an hour.

## Running
- Default run arguments are in `build.gradle` (`ext.ARGS`), edit them there for `./gradlew bootRun`.
- Override on the command line: `./gradlew bootRun --args="-e '/path/to/file.MOV'"`
- Logs go to the console and `logs/media-sorter.log`.
- Build: `./gradlew build`; dependency updates: `./gradlew dependencyUpdates`.

## Git workflow
- Never run `git commit` unless explicitly asked to, for that specific set of changes.
  The user reviews all changes in their own git client's diff view and decides when a
  commit is ready — finishing a task is not by itself a reason to commit.
- Never run `git push` unless explicitly asked to.
- Do not add a `Co-Authored-By: Claude …` trailer (or any other AI attribution) to commit
  messages. Commits are made under the user's identity anyway; the trailer only pollutes
  the history.
