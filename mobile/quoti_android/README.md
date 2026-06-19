# Quoti Android

Native Android app for Quoti, built with Kotlin, Jetpack Compose, and Material 3.

## Run

```powershell
cd C:\dev\open-source\quoti\mobile\quoti_android
.\gradlew.bat :app:installDebug
```

Or open this folder in Android Studio and run the `app` configuration.

## Share Intent Test

```powershell
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "Regarde ca https://x.com/dexerto/status/123" -p com.quoti.android
```

Useful Priority 1 payload checks:

```powershell
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://twitter.com/maya_laurent/status/123?s=46&t=abc" -p com.quoti.android
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://x.com/maya_laurent/status/123" --es android.intent.extra.SUBJECT "Maya Laurent on X: The best product moments are quiet." -p com.quoti.android
```

The app expects Android `ACTION_SEND` text payloads. Without a captured share it shows an empty state instead of a fixture card. URL-only X/Twitter shares show a loading state while Quoti tries public X metadata enrichment, then either display the enriched post or return to the empty state if no usable public metadata is available.

For X/Twitter, the reliable MVP field is the source URL; the app infers platform and handle from `/handle/status/id` URLs, uses shared subject/title text when available, and attempts public X metadata enrichment for URL-only shares.

The Android X app may share internal URLs such as `/i/status/...` plus a generic label like `Shared from X.`. Quoti resolves those through public oEmbed/page metadata when available, filling canonical author, handle, tweet text, source URL, public image media, playable video variants, and reply/quoted-post metadata when X exposes a reliable related status URL. If X does not expose the post metadata, Quoti does not invent tweet text or media.

The first mobile MVP does not expose an editor or alter shared tweet content.

Copy image, share image, and download PNG use an offscreen card renderer, so long cards are exported from the full post model instead of only the visible screen area. Image media is rendered with its real aspect ratio instead of being cropped into a fixed landscape frame. Video previews play looping MP4 variants muted by default with a sound toggle when X exposes one. Video posts switch the primary action to video download, show a Material processing state during generation, and export a 30 fps MP4 Quoti card with the source audio into `Movies/Quoti`; single primary-video exports use a GPU texture composition path when possible, while complex media layouts fall back to the bitmap renderer. PNG export still renders video posts from the poster image.
