import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "../..");
const versionPattern = /^\d+\.\d+\.\d+$/;

function parseArgs(argv) {
  const options = {
    androidVersionCode: process.env.npm_config_android_version_code,
    check: process.env.npm_config_check === "true",
    incrementAndroidCode: process.env.npm_config_increment_android_code === "true",
    version: process.env.npm_config_release_version
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const [name, inlineValue] = arg.split("=", 2);

    if (name === "--check") {
      options.check = true;
      continue;
    }

    if (name === "--increment-android-code") {
      options.incrementAndroidCode = true;
      continue;
    }

    if (name === "--version" || name === "--release-version") {
      options.version = inlineValue ?? argv[++index];
      continue;
    }

    if (name === "--android-version-code") {
      options.androidVersionCode = inlineValue ?? argv[++index];
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

function writeText(relativePath, content) {
  fs.writeFileSync(path.join(rootDir, relativePath), content);
}

function readJson(relativePath) {
  return JSON.parse(readText(relativePath));
}

function stringifyJson(data) {
  return `${JSON.stringify(data, null, 2)}\n`;
}

function updateJsonVersion(relativePath, version, changes, options) {
  const data = readJson(relativePath);
  let changed = false;

  if (data.version !== version) {
    changes.push(`${relativePath}: ${data.version} -> ${version}`);
    data.version = version;
    changed = true;
  }

  if (relativePath === "package-lock.json" && data.packages?.[""]?.version !== version) {
    changes.push(`${relativePath} packages[\"\"].version: ${data.packages[""].version} -> ${version}`);
    data.packages[""].version = version;
    changed = true;
  }

  if (changed && !options.check) {
    writeText(relativePath, stringifyJson(data));
  }
}

function updateAndroidVersion(version, changes, options) {
  const relativePath = "mobile/quoti_android/app/build.gradle.kts";
  const text = readText(relativePath);
  const codeMatch = text.match(/versionCode\s*=\s*(\d+)/);
  const nameMatch = text.match(/versionName\s*=\s*"([^"]+)"/);

  if (!codeMatch || !nameMatch) {
    throw new Error(`Could not find versionCode/versionName in ${relativePath}`);
  }

  const currentCode = Number.parseInt(codeMatch[1], 10);
  let nextCode = currentCode;

  if (options.incrementAndroidCode && options.androidVersionCode !== undefined) {
    throw new Error("Use either --increment-android-code or --android-version-code, not both.");
  }

  if (options.incrementAndroidCode) {
    nextCode = currentCode + 1;
  }

  if (options.androidVersionCode !== undefined) {
    nextCode = Number.parseInt(options.androidVersionCode, 10);

    if (!Number.isInteger(nextCode) || nextCode < 1) {
      throw new Error("--android-version-code must be a positive integer.");
    }
  }

  let nextText = text;

  if (nameMatch[1] !== version) {
    changes.push(`${relativePath} versionName: ${nameMatch[1]} -> ${version}`);
    nextText = nextText.replace(/versionName\s*=\s*"[^"]+"/, `versionName = "${version}"`);
  }

  if (currentCode !== nextCode) {
    changes.push(`${relativePath} versionCode: ${currentCode} -> ${nextCode}`);
    nextText = nextText.replace(/versionCode\s*=\s*\d+/, `versionCode = ${nextCode}`);
  }

  if (nextText !== text && !options.check) {
    writeText(relativePath, nextText);
  }
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const packageJson = readJson("package.json");
  const version = options.version ?? packageJson.version;

  if (!versionPattern.test(version)) {
    throw new Error(`Version must use x.y.z format for Chrome and Android versionName. Received: ${version}`);
  }

  const changes = [];

  updateJsonVersion("package.json", version, changes, options);
  updateJsonVersion("package-lock.json", version, changes, options);
  updateJsonVersion("public/manifest.json", version, changes, options);
  updateAndroidVersion(version, changes, options);

  if (options.check && changes.length > 0) {
    console.error("Release versions are not synchronized:");
    for (const change of changes) {
      console.error(`- ${change}`);
    }
    process.exit(1);
  }

  if (changes.length === 0) {
    console.log(`Release version ${version} is already synchronized.`);
    return;
  }

  if (options.check) {
    console.log(`Release version ${version} is synchronized.`);
    return;
  }

  console.log(`Synchronized release version ${version}:`);
  for (const change of changes) {
    console.log(`- ${change}`);
  }
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
}
