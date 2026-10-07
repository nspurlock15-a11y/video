# LocalStream

An Android app that plays the video files on your phone like a streaming service: shows grouped into seasons and episodes, "Continue watching", resume where you left off, autoplay of the next episode (across seasons), picture-in-picture, background audio, and lock-screen controls.

It works offline and only reads the folders you add.

## Install on your phone (Samsung Galaxy S23 or any Android 8+ phone)

1. On the phone, open this repository's **Releases** page on GitHub (`https://github.com/nspurlock15-a11y/video/releases`) and download **LocalStream.apk** from the newest build.
2. Open the downloaded file. Android will ask you to allow installing apps from your browser or the *My Files* app. Allow it, then tap **Install**.
3. Open **LocalStream**, tap **Add a folder**, and pick the folder that holds your videos (for example `Movies`, `Download/TV`, or a folder on your SD card). Tap **Use this folder** and then **Allow**.

To update, install a newer `LocalStream.apk` over the old one. Your library and watch progress are kept, because every build is signed with the same key (`app/sideload.jks`, committed on purpose so CI builds can update each other).

Every push to this repository builds a new APK with GitHub Actions (`.github/workflows/build.yml`) and publishes it as a release.

## Organising your files

The app works out shows, seasons and episodes from folder and file names. Any of these layouts work:

```
TV/
  Breaking Bad/
    Season 1/
      Breaking.Bad.S01E01.Pilot.720p.mkv
      Breaking.Bad.S01E02.mkv
    Season 2/
      02 - Grilled.mkv            ← season from the folder, episode from the leading number
  The Office/
    The Office S01E01 - Pilot.mp4  ← flat folder, S01E01 in the name
    The Office 1x02.mp4            ← 1x02 also works
Movies/
  Inception (2010)/Inception.mkv   ← a video with no episode number is a movie
  Heat.mp4
```

- Season folders can be named `Season 1`, `S01`, `Series 1`, `Specials`, and so on. Specials (season 0) are listed last and are not part of the autoplay chain.
- Subtitle files next to a video with the same base name (`Pilot.srt`, `Pilot.en.srt`, `.vtt`, `.ass`) are loaded automatically. Subtitles and audio tracks inside the video file (for example in MKV) can be picked from the player's subtitle and settings buttons.
- Put a `poster.jpg` (or `folder.jpg` / `cover.jpg`) in a show's folder to use it as artwork. Otherwise a frame from the first episode is used.
- After adding new episodes, tap the refresh icon on the home screen. The app also rescans each time it starts.

## Features

| | |
|---|---|
| Library | Add one or more folders. Their locations are remembered, so episodes open straight from the app. |
| Continue watching | The home screen opens on the episode you were watching last, with a **Resume** button, plus a row for your other shows in progress. |
| Resume position | Your position in each episode is saved every few seconds, and when you pause, switch episodes or close the app. Reopening an episode continues from there. |
| Autoplay | When an episode ends, the next one starts. The last episode of a season leads into the first episode of the next season. Can be switched off. |
| Up next | A card with a **Play now** button appears during the end credits (timing is configurable). |
| Seasons & episodes | Season tabs, episode thumbnails, progress bars and watched check marks. You can mark episodes or whole seasons as watched or unwatched, or play from the beginning. |
| Picture-in-picture | Pressing Home while a video plays shrinks it into a floating window with previous, play/pause and next buttons. There is also a PiP button in the player. |
| Background audio | Optional (Settings → Background playback). Audio keeps playing with the screen off or the app in the background. |
| Lock screen & notification controls | Play/pause, previous, next and a seek bar on the lock screen, in the notification shade, and on Bluetooth headphones and car stereos. |
| Player | Rewind and fast-forward buttons, a seek bar, subtitle and audio-track selection, playback speed, a Fit/Fill (zoom) toggle, an episode picker, and a sleep timer (15, 30, 45 or 60 minutes, or end of episode). |
| Are you still watching? | Pauses after a set number of autoplayed episodes with no interaction (configurable or off). |
| Skip intro | Optional button that jumps ahead a set amount during the first few minutes. |
| Headphones | Playback pauses when headphones are unplugged, and phone calls and other audio interrupt it correctly. |

## Format support

Playback uses Android's Media3 / ExoPlayer with the phone's hardware decoders: MP4, MKV, WebM, AVI, MOV, TS and more, with H.264, H.265/HEVC, VP9 and AV1 video, and AAC, MP3, Opus, FLAC, AC3 and E-AC3 audio. DTS audio is not supported by most phones. If a file shows a "format your phone can't play" message, the usual fix is to convert its audio track to AAC or AC3.

## Building it yourself

Open the project in Android Studio, or run `./gradlew assembleRelease` with an Android SDK installed. The APK is written to `app/build/outputs/apk/release/`.

Code layout (`app/src/main/java/com/localstream/app/`):

- `data/`: folder scanning and file-name parsing (`LibraryScanner`, `NameParser`), the library and watch-progress store (`Library`), settings, and thumbnails
- `playback/`: `PlaybackService`, a Media3 `MediaSessionService` that owns the player and runs autoplay, progress saving, lock-screen controls, the sleep timer and "still watching"
- `player/`: the full-screen player activity (picture-in-picture, overlays)
- `ui/`: the home, show and settings screens (Jetpack Compose)
