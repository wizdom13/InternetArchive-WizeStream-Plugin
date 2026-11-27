# Internet Archive for WizeStream

A native **WizeStream API 2.0** plugin for public Internet Archive audio and video items. Version **1.0.0**, version code **1**. Requires **WizeStream 1.52.2 or newer** (`1052002`) and **Android 7.0 / API 24 or newer**.

This source is prepared for review. A public repository, publisher signing key and installable signed release still need to be set up; no release is implied by the presence of these workflows.

## What it supports

- Search public audio, movies and recorded-concert (`etree`) items through Archive's documented search API.
- Open `https://archive.org/details/<identifier>` as an item playlist, including items with one recording.
- Select every supported recording in a multi-file item. Explicit `original` file relationships group alternate encodings of one recording into player format choices.
- Open an individual `https://archive.org/download/<identifier>/<filename>` URL, resolve the file against the item's metadata, and play its supported public variants.
- Display item/track titles, public creator names, descriptions, dates, thumbnails and license metadata when available.
- Play progressive **MP4, WebM, MP3, M4A, Ogg Vorbis and Opus** files. Actual codec support still depends on the device and WizeStream's player.
- Use WizeStream's normal player, queues and playlist navigation. Other recordings from the same item appear as partitions and related items.

Search treats entered words as literal text, orders matching items by download count with an identifier tie-breaker, and reads 50 items per page. It stops at Archive's documented 10,000-result limit for sorted pagination. Narrow the search to find more specific items. Search results are item playlists and show an unknown track count until the metadata is opened; the plugin does not make a metadata request for every search result.

There is no home kiosk in this first version. Use WizeStream's search action after selecting Internet Archive, or open a supported Archive URL.

## Item and track identity

An Archive item can contain several independent recordings plus alternate encodings and sidecar files. Selecting an item opens its track list instead of silently choosing one file.

When metadata explicitly connects derivatives to an original public file, that original filename forms the stable stream URL and identity. For example, `walk.flac` with `walk.mp3` and `walk.ogg` derivatives is one track whose player choices are MP3 and Ogg. The plugin does not advertise FLAC playback in version 1.0.0; the original filename identifies the recording while supported derivatives supply playback. A direct link to either supported derivative resolves to the same recording. Separate original files remain separate tracks, even when their titles match.

Grouping follows only documented file metadata. Similar names alone do not establish that files contain the same recording. Missing originals keep an actual available file as their identity. Track numbers, when supplied, determine order; filenames provide a deterministic fallback. File paths with spaces, Unicode or nested directories are encoded safely.

## Current limits

- Public, anonymously accessible audio/video files only. Private files, dark items, borrowing, login-only material and explicit access restrictions return a clear availability error; no restriction is bypassed.
- Collections, creator/channel pages, books, web captures, reviews, subscriptions and search suggestions are outside this first version.
- An available metadata record does not guarantee that a file server is online or that every codec is playable. Item/file removal and remote server failures remain possible.
- FLAC, WAV, MPEG-2, AVI, Theora and other formats without a supported derivative are not advertised. HLS/DASH manifests and other adaptive formats are not extracted.
- Recorded concerts are recordings, not live broadcasts. No live-broadcast detection is advertised.
- No automatic recovery of deleted YouTube videos through the Wayback Machine. Archive metadata search is not a deleted-video recovery system, and an archived webpage is not proof that its media was captured.

## Build and test

Production networking uses the downloader supplied by WizeStream. Kotlin, NewPipe extractor APIs, nanojson, jsoup and other host classes remain compile-only and are not bundled. All implementation and JVM test code is Kotlin.

Use JDK 21 and the WizeStream Gradle wrapper. The validated host SDK baseline is pinned to:

```text
wizdom13/WizeStream @ 68432a57853aa2c0ffdbe4f7c163c2fedd01c18e
```

Clone/check out that host commit in a sibling `WizeStream` directory, install Android platform `android-37.0` and Build Tools `36.1.0`, and set `ANDROID_HOME`. Then:

```sh
cd ../WizeStream
./gradlew :app:pluginSdkJar -DskipFormatKtlint
./gradlew -p ../InternetArchive-WizeStream-Plugin \
  -PpluginSdkJar="$PWD/app/build/outputs/plugin-sdk/wizestream-plugin-api.jar" \
  test pluginPackage
```

The package is written to:

```text
build/repo/internetarchive-wizestream-1.ppp
```

The DEX compilation profile matches the host at min API 23; the manifest declares the actual minimum supported device API as 24. R8/D8 8.13.19 is pinned because the Build Tools bundled compiler cannot rewrite the Kotlin 2.3 metadata used here. The package task verifies that host-provided class namespaces are absent.

The package from `pluginPackage` or ordinary CI is **unsigned**. It is useful for inspection and does not include the signed repository index required for normal installation. CI performs no network calls to Archive in tests and never creates or uses a publisher key.

### Test coverage and fixture sources

Offline tests cover permanent URL handling, ambiguous/foreign URLs, item restrictions and HTTP errors, metadata parsing, supported formats, derivative grouping, independent recordings, stable identities, playlist continuation, public-media search scoping, response positions and pagination bounds. A fixture downloader rejects every unexpected request.

`audio-item.json` is entirely synthetic, with neutral field-recording names. `video-item.json` is a minimal factual metadata extract from the public [Big Buck Bunny trailer item](https://archive.org/details/big-buck-bunny-trailer), checked on 2026-11-27 through its [metadata API](https://archive.org/metadata/big-buck-bunny-trailer). Only the fields needed for parsing tests are kept; there are no media files, account details or access tokens in fixtures. Live metadata and search reads were checked during development; full on-device playback still needs a maintainer smoke test before the first release.

## Publishing a signed repository

Do this only after the project repository has been created and the first version has been reviewed. Use the same publisher key for every subsequent update. Losing or replacing it breaks the trust continuity of existing installations.

1. Run `generateSigningKey` **once** in a secure maintainer checkout. The task creates `signing-key.pk8` (Ed25519 PKCS#8 DER) and its matching `public-key.der`. It never replaces an existing pair. Keep both files out of Git and back them up securely.
2. Add a repository Actions secret named **`SIGNING_KEY`** containing the single-line Base64 encoding of `signing-key.pk8`. Do not put the key in source code, issues, logs or pull requests.
3. Merge the source onto `main` and wait for **Internet Archive plugin CI** to succeed for that exact commit.
4. Manually run **Release Internet Archive plugin** on `main`. This is the only publication trigger. It re-tests the current source, restores the persistent secret into temporary runner files, derives the matching public key, signs the raw SHA-256 package digest, and publishes a `repo` release containing the versioned `.ppp` and `index.json`.

The release workflow checks that publisher identity is unchanged, version codes do not decrease, and an already published version is not replaced by different bytes. It keeps old packages available for cached indexes, uploads the new package before replacing the index, and removes temporary key files even when a step fails. The repository index contains the public key; the private key never belongs in a release.

For a manual local signed build, after the persistent key has been created:

```sh
../WizeStream/gradlew -p . \
  -PpluginSdkJar=../WizeStream/app/build/outputs/plugin-sdk/wizestream-plugin-api.jar \
  test repositoryIndex
```

Increase both `versionCode` and `versionName` in `src/main/resources/plugin.json` for a new release. Branch names should be plain names without slashes, and PRs should contain one commit.

## Install after a signed release exists

In WizeStream, open **Settings → Plugins → Install from project or URL** and enter the published GitHub project URL or the signed `index.json` URL from its `repo` release. Confirm the displayed plugin version and publisher fingerprint. Alternatively, download the `.ppp` and `index.json` from the **same release** and use **Install from file**, selecting the matching index when requested.

No installation link is published in this source-only stage. Ordinary CI's unsigned package is not a substitute for the signed release files.

## API references

- [Internet Archive item and permanent file URLs](https://archive.org/developers/items.html)
- [Metadata API reads and error semantics](https://archive.org/developers/md-read.html)
- [Search API and pagination limits](https://archive.org/help/aboutsearch.htm)
- [Live Music Archive recordings](https://help.archive.org/help/live-music-archive-etree-org/)
- [Wayback Machine limitations](https://help.archive.org/help/using-the-wayback-machine/)

## License

GPL-3.0-or-later. Packaging infrastructure derives from the WizeStream plugin template, which credits the PipePipe plugin template in its source headers. Internet Archive is an independent service; this plugin is an unofficial integration and does not redistribute Archive media.
