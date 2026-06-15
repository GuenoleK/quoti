# Mobile App Phase 3 Roadmap

This roadmap captures the Phase 3 direction for bringing Quoti to mobile, starting with Android and keeping iOS as a first-class follow-up target.

The product goal is simple: Quoti should appear as a share target on mobile, receive a post link or shared content, open a native-feeling Quoti experience, and generate the same kind of elegant context card users already know from the browser extension.

## Phase 3 Positioning

Phase 3 is the mobile companion app phase.

It should not replace the browser extension. It should extend Quoti to the natural mobile workflow:

```text
Open a post
  -> Share
     -> Quoti
        -> Quoti opens with the shared context
           -> Edit, preview, export, or share the card
```

The mobile app should preserve the same product promise as the extension:

> Conversations should travel with their context.

## Current Product Assumptions

- The browser extension remains the current MVP surface.
- The existing `ExtractedPost` model is the conceptual source for mobile post data.
- Quoti remains backend-free by default.
- Social platform APIs should not be required for the mobile MVP.
- Mobile import starts from the system share flow, not from DOM extraction.
- Android comes first, then iOS.

## Recommended Repository Strategy

Start with a lightweight monorepo shape:

```text
quoti/
  src/                    # Current browser extension
  native/                 # Current optional native renderer helper
  mobile/
    quoti_mobile/         # Flutter app
  contracts/
    post.schema.json      # Language-neutral shared Quoti post contract
```

Do not create a separate repository for the first mobile prototype.

A separate repository can be revisited if the mobile app gets its own release cycle, CI/CD pipeline, team ownership, or product roadmap.

## Recommended Stack

Use Flutter for the mobile app.

Reasons:

- one product codebase for Android and iOS;
- fast UI iteration through hot reload;
- strong support for Material Design on Android;
- Cupertino widgets and iOS-aware patterns for Apple platforms;
- unit, widget, and integration testing are first-class Flutter workflows.

## Platform Design Direction

The mobile app should be adaptive, not visually identical on every platform.

Quoti should keep one product identity, one editorial visual language, and one business model, while respecting the interaction patterns of each operating system.

### Android

Android should use Material 3 patterns.

Use Material navigation, controls, sheets, buttons, menus, typography scale, state layers, and system sharing conventions where they match the product.

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

Recommended Flutter feature shape:

```text
lib/
  app/
    quoti_app.dart
    router.dart
    theme/
  core/
    models/
    result/
    platform/
  features/
    incoming_share/
      incoming_share.controller.dart
      incoming_share.service.dart
      adapters/
        android_share_intent.adapter.dart
        ios_share_extension.adapter.dart
    card_editor/
    card_preview/
    export/
    share/
    library/
  dev/
    gallery/
```

The UI should call controllers or view models. It should not know Android intent details, iOS share extension constraints, image encoding internals, or platform storage details.

## Shared Contract Strategy

Flutter should not try to directly reuse TypeScript implementation code.

Instead, share product contracts and fixtures:

```text
contracts/post.schema.json
fixtures/posts/
```

The TypeScript extension and the Dart app can each implement their own typed models from the same contract.

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
              -> Card editor is prefilled
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
              -> Main app can continue editing the Quoti card
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

- Flutter desktop or web for quick UI iteration;
- Android emulator for realistic Android behavior;
- physical Android device for real Sharesheet behavior;
- iOS simulator for visual review once macOS/Xcode is available;
- physical iPhone for real Share Extension behavior before release.

## Automated Testing Strategy

Use Flutter's standard testing layers:

- unit tests for models, parsing, formatting, and card export requests;
- widget tests for card preview, editor states, theme controls, and empty states;
- integration tests for import, edit, preview, export, and share flows.

The first mobile test suite should focus on the product core:

- parse incoming share payload;
- normalize into Quoti post data;
- render expected editor state;
- switch theme and content mode;
- create an export request.

## Phase 3 Work Items

| Step | Status | Notes |
| --- | --- | --- |
| Confirm Flutter as the mobile app stack | Planned | Android first, iOS second, adaptive UI from the start. |
| Add language-neutral post contract | Planned | Start with `contracts/post.schema.json`. |
| Create Flutter app under `mobile/quoti_mobile` | Planned | Keep the first prototype in the existing repo. |
| Create mobile app architecture document | Planned | Document feature boundaries and controller/service/adapter usage. |
| Build dev gallery with post fixtures | Planned | Enables visual QA without real share integrations. |
| Implement Android incoming share prototype | Planned | Receive text and URLs from Android Sharesheet. |
| Implement first card editor and preview | Planned | Use Quoti identity, with adaptive platform shell. |
| Add export image prototype | Planned | Generate a shareable image from the card. |
| Add Flutter unit and widget tests | Planned | Cover core normalization and UI states. |
| Validate on Android emulator and physical device | Planned | Sharesheet behavior must be tested on a real device. |
| Plan iOS Share Extension implementation | Planned | Account for App Groups and extension limitations. |

## Phase 3 Definition Of Done

- Quoti appears as a share target on Android for text and URLs.
- Selecting Quoti from the Android Sharesheet opens the app with prefilled shared context.
- The app displays a Quoti card preview from the incoming share payload.
- The user can adjust at least theme and content mode.
- The user can export or share the generated image.
- The Android UI follows Material 3 expectations.
- The iOS architecture plan accounts for Share Extension constraints.
- A development gallery exists for visual review of core card states.
- Unit and widget tests cover the core post normalization and editor states.
- The browser extension remains unaffected.

## Risks And Constraints

- Mobile apps cannot rely on browser DOM extraction.
- Shared URLs may not include enough metadata to fully reconstruct a post.
- Some platforms may share only a URL, not rich content.
- iOS Share Extensions cannot behave exactly like Android share intents.
- iOS implementation requires macOS and Xcode for real build and device testing.
- The generated card must stay visually consistent even when the app shell adapts per platform.

## References

- Flutter app architecture: https://docs.flutter.dev/app-architecture
- Flutter testing overview: https://docs.flutter.dev/testing/overview
- Flutter integration tests: https://docs.flutter.dev/testing/integration-tests
- Flutter Material Design: https://docs.flutter.dev/ui/design/material
- Flutter Cupertino widgets: https://docs.flutter.dev/ui/widgets/cupertino
- Android receiving shared data: https://developer.android.com/training/sharing/receive
- Android sending shared data: https://developer.android.com/training/sharing/send
- Apple Human Interface Guidelines: https://developer.apple.com/design/human-interface-guidelines
- Apple activity views: https://developer.apple.com/design/human-interface-guidelines/activity-views
- Apple App Extension overview: https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionOverview.html
- Apple App Groups: https://developer.apple.com/documentation/Xcode/configuring-app-groups
