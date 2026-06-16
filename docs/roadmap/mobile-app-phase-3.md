# Mobile App Phase 3 Roadmap

This roadmap captures the Phase 3 direction for bringing Quoti to mobile, starting with native Android. iOS remains a future follow-up target, deferred until a macOS/Xcode and iPhone testing environment is available.

The product goal is simple: Quoti should appear as a share target on mobile, receive a post link or shared content, open a native-feeling Quoti experience, and generate the same kind of elegant context card users already know from the browser extension.

## Phase 3 Positioning

Phase 3 is the mobile companion app phase.

It should not replace the browser extension. It should extend Quoti to the natural mobile workflow:

```text
Open a post
  -> Share
     -> Quoti
        -> Quoti opens with the shared context
           -> Preview, export, or share the card
```

The mobile app should preserve the same product promise as the extension:

> Conversations should travel with their context.

## Current Product Assumptions

- The browser extension remains the current MVP surface.
- The existing `ExtractedPost` model is the conceptual source for mobile post data.
- Quoti remains backend-free by default.
- Social platform APIs should not be required for the mobile MVP.
- Mobile import starts from the system share flow, not from DOM extraction.
- Android comes first. iOS comes later when the project has the right Apple development and testing environment.

## Roadmap Status

Status legend:

- `Planned`: documented but not started.
- `In progress`: actively being implemented.
- `Blocked`: needs a decision, dependency, or technical proof.
- `Done`: shipped or committed for the current milestone.

## Recommended Repository Strategy

Start with a lightweight monorepo shape:

```text
quoti/
  src/                    # Current browser extension
  native/                 # Current optional native renderer helper
  mobile/
    quoti_android/        # Native Android app: Kotlin, Compose, Material 3
  contracts/
    post.schema.json      # Language-neutral shared Quoti post contract
```

Do not create a separate repository for the first mobile prototype.

A separate repository can be revisited if the mobile app gets its own release cycle, CI/CD pipeline, team ownership, or product roadmap.

## Recommended Stack

Use native Android first, with Kotlin, Jetpack Compose, and Material 3.

Reasons:

- Android is the first mobile release target;
- the core workflow depends on native Android Sharesheet and intents;
- Material 3 is a product requirement, not just a styling preference;
- Compose gives direct access to Material 3 components, dynamic color, previews, and Android Studio tooling;
- native Android keeps export, sharing, storage, and system integration straightforward.

The earlier Flutter prototype has been removed. Keep the mobile workspace focused on the native Android app until there is a concrete iOS implementation path.

## Platform Design Direction

The mobile app should be adaptive, not visually identical on every platform.

Quoti should keep one product identity, one editorial visual language, and one business model, while respecting the interaction patterns of each operating system.

### Android

Android should use Material 3 patterns.

Use Material navigation, controls, sheets, buttons, segmented controls, menus, typography scale, state layers, dynamic color, and system sharing conventions where they match the product.

### iOS

iOS should follow Apple's Human Interface Guidelines.

Use Cupertino-style navigation, switches, pickers, transitions, sheets, and platform-native expectations where they make the app feel at home on iPhone.

### Quoti Identity

The Quoti generated card remains platform-consistent.

The app shell adapts to the OS. The exported card remains recognizably Quoti.

## Mobile Architecture

Use the existing Quoti preference for:

```text
controller -> service -> adapter
```

Recommended native Android feature shape:

```text
app/src/main/kotlin/com/quoti/android/
  app/
  core/
    models/
  data/
  features/
    incoming_share/
    card_preview/
    export/
    share/
    library/
  ui/
    theme/
```

The UI should call controllers, view models, or state holders. It should not know Android intent details, image encoding internals, or platform storage details.

## Shared Contract Strategy

The Android app should not try to directly reuse TypeScript implementation code.

Instead, share product contracts and fixtures:

```text
contracts/post.schema.json
fixtures/posts/
```

Current baseline:

- `contracts/post.schema.json` defines the initial language-neutral post contract.
- `fixtures/posts/` contains the first development gallery inputs.

The TypeScript extension and the Kotlin app can each implement their own typed models from the same contract.

Initial shared concepts:

- social platform;
- extracted post;
- related post;
- image media;
- video media;
- card theme;
- card content mode;
- export format.

## Incoming Share Workflow

Incoming share is the core Phase 3 workflow.

### Android Target Workflow

Android should register Quoti as a compatible target for shared text and URLs.

Target experience:

```text
User taps Share in another app
  -> Android Sharesheet appears
     -> User selects Quoti
        -> Quoti app opens
           -> Shared text or URL is parsed
              -> Card preview is prefilled
```

Android implementation will likely use `ACTION_SEND` and intent filters for text, URLs, and later image or video inputs.

### iOS Target Workflow

iOS should use a Share Extension.

Target experience:

```text
User taps Share in another app
  -> iOS Share Sheet appears
     -> User selects Quoti
        -> Quoti Share Extension receives the content
           -> Shared content is stored as a draft
              -> Main app can preview and export the Quoti card
```

iOS has stricter extension boundaries than Android. The share extension and containing app should communicate through the platform-supported shared container approach when needed.

## Preview And Manual Testing Strategy

Phase 3 should include a built-in development gallery before deep platform integrations.

Recommended gallery:

```text
Short text post
Long text post
Post with reply context
Post with image
Post with video poster
Missing author data
Light theme
Dark theme
Square export
Portrait export
Landscape export
```

This gallery lets the team see the mobile UI at work without depending on a real share source during early design iteration.

Recommended manual preview targets:

- Android emulator for realistic Android behavior;
- physical Android device for real Sharesheet behavior;
- iOS simulator for visual review once macOS/Xcode is available;
- physical iPhone for real Share Extension behavior before release.

## Automated Testing Strategy

Use Android's standard testing layers:

- JVM unit tests for models, parsing, formatting, and card export requests;
- Compose UI tests for preview, theme controls, and empty states;
- Android instrumentation tests for import, preview, export, and share flows.

The first mobile test suite should focus on the product core:

- parse incoming share payload;
- normalize into Quoti post data;
- render expected preview state;
- switch theme and content mode;
- create an export request.

## Phase 3 Work Items

| Step | Status | Notes |
| --- | --- | --- |
| Pivot Android app stack to Kotlin + Compose + Material 3 | Done | Native Android is now the primary app path. |
| Add language-neutral post contract | Done | Initial schema lives in `contracts/post.schema.json`. |
| Remove Flutter prototype | Done | `mobile/quoti_mobile` was removed to keep Phase 3 focused on native Android. |
| Create native Android app under `mobile/quoti_android` | Done | Kotlin, Jetpack Compose, Material 3, own Gradle wrapper. |
| Create mobile app architecture document | Done | See `docs/architecture/mobile-app-architecture.md`. |
| Build dev gallery with post fixtures | Done | Fixtures exist and feed the mobile preview; fixture selection is not exposed in the default product shell. |
| Implement Android incoming share prototype | Done | Receive shared text and URLs from Android Sharesheet into the Compose app. |
| Implement first card preview | Done | Compose card preview is backed by normalized incoming-share data. The MVP does not allow editing shared post content. |
| Add export image prototype | Done | PNG export, image clipboard, source URL clipboard item, and Android image share intent are implemented. |
| Add Android unit and Compose tests | In progress | JVM tests cover incoming share normalization and export naming; Compose instrumentation covers the primary controls. |
| Validate on Android emulator and physical device | In progress | Emulator validation passes; physical Pixel 9 Pro APK install and real Sharesheet behavior still need product-path validation. |
| Plan iOS Share Extension implementation | Planned | Account for App Groups and extension limitations. |

## Next MVP Priorities

The next work should turn the current functional prototype into the smallest reliable mobile MVP.

### Priority 1: Core Share-To-Card Flow

- Validate the real flow from X/Twitter: share a post to Quoti, parse the incoming Android payload, show a correct card preview, then copy, share, or download it.
- Replace fixture-driven preview data with real incoming shared data wherever the Android share payload provides enough information.
- Identify what X/Twitter actually sends on Android: text, URL, author, source handle, media, or only a link.
- Do not add an edit surface in the first MVP; when payloads are incomplete, keep the shared data honest and avoid altering tweet content.
- Stabilize card export beyond visible-screen capture by adding an offscreen card renderer for long cards.

Current implementation notes:

- Incoming Android text shares now drive the default card state when present; no-share launches use an explicit empty state.
- X/Twitter URLs infer platform and handle from `/handle/status/id`; subject/title text is used when the sender provides richer metadata.
- The no-share app state is now an explicit empty state instead of a fixture card.
- URL-only X/Twitter shares, including Android X `/i/status/...` links, show a loading state while they are enriched through public oEmbed/page metadata when available.
- The app does not expose an edit surface; when public metadata is unavailable, X/Twitter shares remain source-only instead of inventing tweet text or media.
- Public X page enrichment now imports multiple image media, playable video variants, and reliable reply/quoted-post metadata when exposed by X.
- Compose preview and offscreen PNG export preserve media aspect ratio instead of cropping into a fixed landscape frame.
- Copy image, share image, and download PNG use an offscreen model-driven renderer instead of visible-screen capture.
- Video previews play muted looping MP4 variants when X exposes one; MP4 video-card export remains a follow-up, and PNG exports still use the poster image.
- Physical X/Twitter app validation still needs a device pass to record the exact payload variants sent by the installed X app version.

### Priority 2: Product Completeness

- Simplify settings to only the controls that matter for the MVP.
- Persist user preferences for card tone, text/media mode, and source actions.
- Add proper Material 3 states for loading, empty, error, success, and export/share progress.
- Either implement real video download/export or hide the video action until it is production-ready.

### Priority 3: Mobile Polish

- Make the floating toolbar actions easier to understand while staying close to Material 3 guidance.
- Review motion, state layers, haptics, and sheet/button feedback on emulator and physical device.
- Test dark mode, status bar, navigation bar, Android Sharesheet, copy image, share image, and PNG download on the Pixel 9 Pro.
- Prepare a clean installable build path: app icon, version naming, signing, release APK/AAB, and install instructions.

## Phase 3 Definition Of Done

- Quoti appears as a share target on Android for text and URLs.
- Selecting Quoti from the Android Sharesheet opens the app with prefilled shared context.
- The app displays a Quoti card preview from the incoming share payload.
- The user can adjust at least theme and content mode.
- The user can export or share the generated image.
- The Android UI follows Material 3 expectations.
- The iOS architecture plan accounts for Share Extension constraints.
- A development gallery exists for visual review of core card states.
- Unit and widget tests cover the core post normalization and preview states.
- The browser extension remains unaffected.

## Risks And Constraints

- Mobile apps cannot rely on browser DOM extraction.
- Shared URLs may not include enough metadata to fully reconstruct a post.
- Some platforms may share only a URL, not rich content.
- iOS Share Extensions cannot behave exactly like Android share intents.
- iOS implementation requires macOS and Xcode for real build and device testing.
- The generated card must stay visually consistent even when the app shell adapts per platform.

## References

- Material 3 in Jetpack Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Set up Compose dependencies and compiler: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Jetpack Compose testing: https://developer.android.com/develop/ui/compose/testing
- Android receiving shared data: https://developer.android.com/training/sharing/receive
- Android sending shared data: https://developer.android.com/training/sharing/send
- Apple Human Interface Guidelines: https://developer.apple.com/design/human-interface-guidelines
- Apple activity views: https://developer.apple.com/design/human-interface-guidelines/activity-views
- Apple App Extension overview: https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionOverview.html
- Apple App Groups: https://developer.apple.com/documentation/Xcode/configuring-app-groups
