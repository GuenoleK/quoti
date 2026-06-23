import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "../..");
const androidDir = path.join(rootDir, "mobile/quoti_android");
const versionPattern = /^\d+\.\d+\.\d+$/;

function parseArgs(argv) {
  const options = {
    outputDir: process.env.npm_config_output_dir,
    skipAndroid: process.env.npm_config_skip_android === "true",
    skipBuild: process.env.npm_config_skip_build === "true",
    skipExtension: process.env.npm_config_skip_extension === "true",
    skipTests: process.env.npm_config_skip_tests === "true",
    version: process.env.npm_config_release_version
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const [name, inlineValue] = arg.split("=", 2);

    if (name === "--output-dir") {
      options.outputDir = inlineValue ?? argv[++index];
      continue;
    }

    if (name === "--skip-android") {
      options.skipAndroid = true;
      continue;
    }

    if (name === "--skip-build") {
      options.skipBuild = true;
      continue;
    }

    if (name === "--skip-extension") {
      options.skipExtension = true;
      continue;
    }

    if (name === "--skip-tests") {
      options.skipTests = true;
      continue;
    }

    if (name === "--version" || name === "--release-version") {
      options.version = inlineValue ?? argv[++index];
      continue;
    }

    if (!arg.startsWith("--") && options.version === undefined) {
      options.version = arg;
      continue;
    }

    throw new Error(`Unknown argument: ${arg}`);
  }

  return options;
}

function readText(relativePath) {
  return fs.readFileSync(path.join(rootDir, relativePath), "utf8");
}

function readJson(relativePath) {
  return JSON.parse(readText(relativePath));
}

function readAndroidVersion() {
  const text = readText("mobile/quoti_android/app/build.gradle.kts");
  const codeMatch = text.match(/versionCode\s*=\s*(\d+)/);
  const nameMatch = text.match(/versionName\s*=\s*"([^"]+)"/);

  if (!codeMatch || !nameMatch) {
    throw new Error("Could not read Android versionCode/versionName.");
  }

  return {
    versionCode: Number.parseInt(codeMatch[1], 10),
    versionName: nameMatch[1]
  };
}

function run(command, cwd) {
  console.log(`\n> ${command}`);
  const result = spawnSync(command, {
    cwd,
    shell: true,
    stdio: "inherit",
    windowsHide: true
  });

  if (result.status !== 0) {
    throw new Error(`Command failed with exit code ${result.status}: ${command}`);
  }
}

function runCapture(command, cwd) {
  const result = spawnSync(command, {
    cwd,
    encoding: "utf8",
    shell: true,
    stdio: ["ignore", "pipe", "ignore"],
    windowsHide: true
  });

  if (result.status !== 0) {
    return "unknown";
  }

  return result.stdout.trim() || "unknown";
}

function ensureVersionAlignment(version, options) {
  const packageVersion = readJson("package.json").version;
  const extensionVersion = readJson("public/manifest.json").version;
  const androidVersion = readAndroidVersion();
  const mismatches = [];

  if (packageVersion !== version) {
    mismatches.push(`package.json is ${packageVersion}`);
  }

  if (!options.skipExtension && extensionVersion !== version) {
    mismatches.push(`public/manifest.json is ${extensionVersion}`);
  }

  if (!options.skipAndroid && androidVersion.versionName !== version) {
    mismatches.push(`Android versionName is ${androidVersion.versionName}`);
  }

  if (mismatches.length > 0) {
    throw new Error(
      `Release version ${version} is not synchronized:\n` +
        mismatches.map((entry) => `- ${entry}`).join("\n") +
        "\nRun npm run release:sync-version first."
    );
  }

  return androidVersion;
}

function sha256(filePath) {
  const hash = crypto.createHash("sha256");
  hash.update(fs.readFileSync(filePath));
  return hash.digest("hex");
}

function escapePwsh(value) {
  return value.replace(/'/g, "''");
}

function createZipFromDirectory(sourceDir, zipPath) {
  fs.mkdirSync(path.dirname(zipPath), { recursive: true });

  if (process.platform === "win32") {
    const command = [
      "powershell",
      "-NoProfile",
      "-ExecutionPolicy Bypass",
      "-Command",
      `"$ErrorActionPreference='Stop'; if (Test-Path -LiteralPath '${escapePwsh(zipPath)}') { Remove-Item -LiteralPath '${escapePwsh(zipPath)}' -Force }; Compress-Archive -Path '${escapePwsh(path.join(sourceDir, "*"))}' -DestinationPath '${escapePwsh(zipPath)}' -Force"`
    ].join(" ");
    run(command, rootDir);
    return;
  }

  if (fs.existsSync(zipPath)) {
    fs.rmSync(zipPath, { force: true });
  }

  run(`zip -qr "${zipPath}" .`, sourceDir);
}

function copyReleaseNotes(platform, version, outputDir) {
  const source = path.join(rootDir, `docs/release/notes/${platform}/${version}.md`);

  if (!fs.existsSync(source)) {
    throw new Error(`Missing release notes: ${path.relative(rootDir, source)}`);
  }

  const destination = path.join(outputDir, `quoti-${platform}-v${version}-release-notes.md`);
  fs.copyFileSync(source, destination);

  return {
    content: fs.readFileSync(source, "utf8").trim(),
    file: destination
  };
}

function formatNoteBody(markdown) {
  return markdown
    .replace(/^# .+?(\r?\n)+/, "")
    .replace(/^(#{2,6})(?= )/gm, "#$1")
    .trim();
}

function formatLocalDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

function writeAggregateNotes({ androidNotes, commonNotes, extensionNotes, outputDir, version }) {
  const commit = runCapture("git rev-parse --short HEAD", rootDir);
  const releaseDate = formatLocalDate(new Date());
  const sections = [`# Quoti ${version}`, "", `Release date: ${releaseDate}`, `Git commit: \`${commit}\``];

  if (commonNotes) {
    sections.push("", "## Common", "", formatNoteBody(commonNotes.content));
  }

  if (androidNotes) {
    sections.push("", "## Android", "", formatNoteBody(androidNotes.content));
  }

  if (extensionNotes) {
    sections.push("", "## Extension", "", formatNoteBody(extensionNotes.content));
  }

  const file = path.join(outputDir, `quoti-v${version}-release-notes.md`);
  fs.writeFileSync(file, `${sections.join("\n")}\n`);

  return {
    commit,
    file,
    releaseDate
  };
}

function writeChecksums(artifacts, outputDir) {
  const lines = artifacts.map((artifact) => `${artifact.sha256}  ${path.basename(artifact.file)}`);
  const file = path.join(outputDir, "SHA256SUMS.txt");
  fs.writeFileSync(file, `${lines.join("\n")}\n`);
  return file;
}

function packageExtension({ artifacts, outputDir, skipBuild, version }) {
  if (!skipBuild) {
    run("npm run build", rootDir);
  }

  const distDir = path.join(rootDir, "dist");

  if (!fs.existsSync(path.join(distDir, "manifest.json"))) {
    throw new Error("Extension dist/manifest.json was not found. Run npm run build first.");
  }

  const zipFile = path.join(outputDir, `quoti-extension-v${version}.zip`);
  createZipFromDirectory(distDir, zipFile);

  artifacts.push({
    file: zipFile,
    kind: "extension",
    sha256: sha256(zipFile)
  });
}

function packageAndroid({ androidVersion, artifacts, outputDir, skipBuild, skipTests, version }) {
  if (!skipBuild) {
    const gradle = process.platform === "win32" ? ".\\gradlew.bat" : "./gradlew";
    const tasks = skipTests ? [":app:assembleDebug"] : [":app:testDebugUnitTest", ":app:assembleDebug"];
    run(`${gradle} ${tasks.join(" ")}`, androidDir);
  }

  const apkSource = path.join(androidDir, "app/build/outputs/apk/debug/app-debug.apk");

  if (!fs.existsSync(apkSource)) {
    throw new Error("Android debug APK was not found. Run the Android build first.");
  }

  const apkFile = path.join(outputDir, `quoti-android-v${version}+${androidVersion.versionCode}-debug.apk`);
  fs.copyFileSync(apkSource, apkFile);

  artifacts.push({
    file: apkFile,
    kind: "android-debug-apk",
    sha256: sha256(apkFile)
  });
}

function writeManifest({ aggregateNotes, androidVersion, artifacts, notes, outputDir, version }) {
  const manifest = {
    version,
    releaseDate: aggregateNotes.releaseDate,
    gitCommit: aggregateNotes.commit,
    common: notes.common
      ? {
          notes: path.basename(notes.common.file)
        }
      : undefined,
    android: notes.android
      ? {
          versionCode: androidVersion.versionCode,
          versionName: androidVersion.versionName,
          notes: path.basename(notes.android.file)
        }
      : undefined,
    extension: notes.extension
      ? {
          version,
          notes: path.basename(notes.extension.file)
        }
      : undefined,
    aggregateNotes: path.basename(aggregateNotes.file),
    artifacts: artifacts.map((artifact) => ({
      kind: artifact.kind,
      file: path.basename(artifact.file),
      sha256: artifact.sha256
    }))
  };

  fs.writeFileSync(path.join(outputDir, "release-manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const version = options.version ?? readJson("package.json").version;

  if (!versionPattern.test(version)) {
    throw new Error(`Version must use x.y.z format. Received: ${version}`);
  }

  if (options.skipAndroid && options.skipExtension) {
    throw new Error("Nothing to package: both --skip-android and --skip-extension were provided.");
  }

  const outputDir = path.resolve(rootDir, options.outputDir ?? `release/packages/v${version}`);
  const androidVersion = ensureVersionAlignment(version, options);
  const artifacts = [];
  const notes = {};

  fs.mkdirSync(outputDir, { recursive: true });
  notes.common = copyReleaseNotes("common", version, outputDir);

  if (!options.skipAndroid) {
    notes.android = copyReleaseNotes("android", version, outputDir);
    packageAndroid({
      androidVersion,
      artifacts,
      outputDir,
      skipBuild: options.skipBuild,
      skipTests: options.skipTests,
      version
    });
  }

  if (!options.skipExtension) {
    notes.extension = copyReleaseNotes("extension", version, outputDir);
    packageExtension({
      artifacts,
      outputDir,
      skipBuild: options.skipBuild,
      version
    });
  }

  const aggregateNotes = writeAggregateNotes({
    androidNotes: notes.android,
    commonNotes: notes.common,
    extensionNotes: notes.extension,
    outputDir,
    version
  });
  const checksumsFile = writeChecksums(artifacts, outputDir);

  writeManifest({
    aggregateNotes,
    androidVersion,
    artifacts,
    notes,
    outputDir,
    version
  });

  console.log("\nRelease package created:");
  console.log(`- ${path.relative(rootDir, outputDir)}`);
  for (const artifact of artifacts) {
    console.log(`- ${path.basename(artifact.file)} ${artifact.sha256}`);
  }
  console.log(`- ${path.basename(aggregateNotes.file)}`);
  console.log(`- ${path.basename(checksumsFile)}`);
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
}
