# Mobile App Architecture

This document defines the initial architecture for Quoti Phase 3: the mobile companion app.

The mobile app extends Quoti into the system share workflow. It should not replace the browser extension, introduce a backend requirement, or depend on social platform APIs.

## Current Status

- Native Android is the selected stack for the first mobile app.
- The Android app uses Kotlin, Jetpack Compose, and Material 3.
- iOS remains a future follow-up target through an architecture that accounts for Share Extension constraints, but it is deferred until a macOS/Xcode and iPhone testing environment is available.
- `contracts/post.schema.json` is the shared post contract.
- `fixtures/posts` contains the first demo and visual QA inputs.
- The native Android app lives at `mobile/quoti_android`.
- The visible Android shell mirrors the extension's simple capture flow.
- Runtime demo assets are mirrored under `mobile/quoti_android/app/src/main/assets/fixtures/posts` so Android can bundle them. Keep `fixtures/posts` as the canonical source.

## Repository Shape

```text
quoti/
  contracts/
    post.schema.json
  fixtures/
    posts/
  mobile/
    quoti_android/
  src/
```

`src/` remains the browser extension. `mobile/quoti_android/` owns the native Android app. Shared product contracts live outside the runtimes.

## Runtime Boundaries

The mobile app should follow the same service preference as the extension:

```text
controller -> service -> adapter
```

- Controllers own feature contracts and product-level errors.
- Services own normalization, orchestration, and business rules.
- Adapters own Android intents, iOS Share Extension handoff, platform storage, image export APIs, and native sharing APIs.

Compose UI should call controllers, view models, or state holders. Composables should not know Android intent extras, image encoding details, or platform storage details.

## Suggested Android Layout

```text
app/src/main/kotlin/com/quoti/android/
  app/
  core/
    model/
  data/
  features/
    incoming_share/
    card_editor/
    card_preview/
    export/
    share/
    library/
  share/
  ui/
    theme/
```

Create folders when there is a real feature boundary. Do not split small widgets or helpers into many files before the app needs it.

## Shared Contract

Mobile should implement Kotlin models from `contracts/post.schema.json`. It should not import or execute TypeScript from the browser extension.

Initial shared concepts:

- social platform;
- extracted post;
- related post;
- image media;
- video media;
- card theme;
- card content mode;
- export format.

Fixtures in `fixtures/posts` are the first source of truth for demo states, visual QA, and cross-runtime normalization checks.

## Incoming Share Feature

Incoming share is the first platform integration.

Target workflow:

```text
System share sheet
  -> Quoti mobile app
     -> Android intent reader
        -> incoming share normalizer
           -> normalized Quoti post draft
              -> Compose card preview/editor
```

The first Android implementation receives shared text and URLs through `ACTION_SEND`. It should normalize the payload into a draft even when metadata is incomplete.

The iOS adapter should be designed around Share Extension handoff. The extension should store incoming content in a supported shared container so the containing app can continue editing the draft.

## Card Editor And Preview

The mobile card preview should preserve Quoti's generated-card identity:

- quote content remains the visual priority;
- metadata is present but quiet;
- media stays attached to the quote;
- light and dark themes match the browser extension;
- content mode supports `text-only` and `with-media`.

The mobile shell should use Material 3 on Android and Cupertino expectations on iOS. The exported card should remain platform-consistent.

## Demo Fixtures

Before deep platform integration, use demo fixtures from `fixtures/posts`.

Initial states:

- short text post;
- long text post;
- post with reply context;
- post with image;
- post with video poster;
- missing author data;
- light theme;
- dark theme;
- square export;
- portrait export;
- landscape export.

The app should run without a real share source and should be usable for visual QA on Android emulator and physical Android devices. iOS simulator review can be added later when macOS/Xcode is available.

The visible mobile screen should stay simple like the extension: one captured post preview, theme/content-mode controls, and direct export/copy actions. Fixture selection can return later behind a developer-only route if needed, but it should not be the default product surface.

## Testing Strategy

Use Android's standard testing layers:

- JVM unit tests for model parsing, incoming share normalization, and export requests;
- Compose UI tests for editor states, card preview, theme controls, content mode controls, and empty states;
- Android instrumentation tests for import, edit, preview, export, and share flows.

The first useful tests should cover:

- loading every fixture from `fixtures/posts`;
- normalizing shared text and URL payloads;
- rendering a prefilled editor state;
- switching theme and content mode;
- creating an export request without platform-specific UI knowledge.

## Setup Notes

Run native Android checks from the app folder:

```powershell
cd mobile/quoti_android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

For local command-line builds, create a local `local.properties` pointing to the Android SDK if Gradle cannot find `ANDROID_HOME`. Do not commit it.

- keep generated platform folders inside `mobile/quoti_android`;
- avoid committing build outputs such as `build/`, `.gradle/`, `.kotlin/`, `local.properties`, and generated diagnostics;
- add app-specific setup notes under `mobile/quoti_android/README.md`;
- update `docs/roadmap/mobile-app-phase-3.md` when milestones move.
