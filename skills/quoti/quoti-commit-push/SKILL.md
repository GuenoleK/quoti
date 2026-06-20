---
name: quoti-commit-push
description: Commit and push changes in the Quoti repository with the project's scope-based commit message conventions, relevant verification commands, existing unpushed commit checks, and workspace cleanup rules. Use when the user asks Codex to commit, push, or commit and push work in this repo.
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

- Android app only: files under `mobile/quoti_android/`, Android app docs, Android fixtures, or release metadata.
- Extension only: files under `src/`, `public/`, extension HTML entrypoints, browser-extension docs, or generated extension assets.
- Mobile and extension together: meaningful product/runtime changes in both Android and extension surfaces.
- Repo workflow/docs only: skills, agent instructions, or documentation that does not primarily change Android or extension behavior.

Supporting docs, skills, tests, or version bumps follow the main product surface. For example, an Android exporter fix plus a repo skill should still use the Android app convention.

3. Choose the commit message convention from recent history:

- Android app: `Android app : <concise change>`
- Extension: `Extension : <concise change>`
- Both mobile and extension: `Mobile/Extension : <concise change>`
- Repo workflow/docs only: use a concise sentence without a product prefix unless recent history shows a better local pattern.

Prefer lowercase after the colon when it reads naturally, matching recent commits such as:

```text
Android app : reduce video source size for exports
Extension : handle media-only captured posts
Mobile/Extension : refine app icons
```

Always re-check recent commit subjects before committing; the log is the source of truth when conventions drift.

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
