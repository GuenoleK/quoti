---
name: quoti-commit-push
description: Commit and push changes in the Quoti repository with mandatory English commit messages prefixed by Android, Extension, or Android + Extension, plus relevant verification commands, existing unpushed commit checks, and workspace cleanup rules. Use when the user asks Codex to commit, push, or commit and push work in this repo.
---

# Quoti Commit Push

## Workflow

1. Inspect the current branch and working tree:

```powershell
git status --short --branch
git log --oneline -n 20
git log --oneline --decorate origin/main..HEAD
```

If local commits are already ahead of the remote, identify them before pushing. Do not rewrite, squash, or drop user commits unless the user explicitly asks.

2. Classify the change by touched surface:

- Android: files under `mobile/quoti_android/`, Android app docs, Android fixtures, or Android release metadata.
- Extension: files under `src/`, `public/`, extension HTML entrypoints, browser-extension docs, generated extension assets, or extension release metadata.
- Android + Extension: meaningful changes across both Android and extension surfaces, or shared repository workflow/docs changes that govern both surfaces.

Supporting docs, skills, tests, or version bumps follow the main product surface. For example, an Android exporter fix plus a repo skill should still use the Android prefix.

3. Choose the commit message convention from the required project format:

- Android: `Android: <concise English change>`
- Extension: `Extension: <concise English change>`
- Android + Extension: `Android + Extension: <concise English change>`

The prefix is mandatory. Do not use unprefixed commit subjects for Quoti commits. Do not replace these prefixes with older variants such as `Android app`, `Mobile/Extension`, or repo-only subjects.

Prefer a short, imperative or descriptive English message after the colon:

```text
Android: reduce video source size for exports
Extension: wrap long card links
Android + Extension: refine shared fixtures
```

Review recent commit subjects for wording style only. The prefix rule above remains the source of truth even when older commits differ.

4. Run relevant verification before committing:

- Android app changes:

```powershell
cd mobile/quoti_android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

- Extension source, style, asset, or generated-card changes:

```powershell
npm run build
```

Use focused tests when the change has a narrower known check. Report any verification that could not run.

5. Clean Codex artifacts before the final commit:

- Remove `.codex`, `.codex-*`, `.codex-remote-attachments`, logs, screenshots, and temporary Codex artifacts from project workspaces.
- Do not delete source changes, required build outputs, Gradle caches, or user files.

6. Commit and push:

```powershell
git add <changed files>
git commit -m "<message>"
git push
```

After pushing, report the commit hash, branch, pushed remote, tests run, and any skipped checks.
