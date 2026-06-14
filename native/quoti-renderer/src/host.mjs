import { createReadStream } from "node:fs";
import { access, mkdtemp, rm, stat, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const hostName = "com.quoti.renderer";
const requestTimeoutMs = 5 * 60 * 1000;
const sourceRoot = path.dirname(fileURLToPath(import.meta.url));
const hostRoot = path.resolve(sourceRoot, "..");
const activeJobs = new Map();

class NativeRenderError extends Error {
  constructor(code, message, cause) {
    super(message);
    this.name = "NativeRenderError";
    this.code = code;
    this.cause = cause;
  }
}

if (process.argv.includes("--check")) {
  const ffmpegPath = await resolveBundledFfmpeg();
  process.stdout.write(`${hostName} ready\n${ffmpegPath}\n`);
  process.exit(0);
}

let readBuffer = Buffer.alloc(0);

process.stdin.on("data", (chunk) => {
  readBuffer = Buffer.concat([readBuffer, chunk]);
  consumeNativeMessages();
});

process.stdin.on("end", () => {
  void cleanupAll().finally(() => process.exit(0));
});

process.on("SIGTERM", () => {
  void cleanupAll().finally(() => process.exit(0));
});

process.on("SIGINT", () => {
  void cleanupAll().finally(() => process.exit(0));
});

function consumeNativeMessages() {
  while (readBuffer.length >= 4) {
    const messageLength = readBuffer.readUInt32LE(0);

    if (readBuffer.length < messageLength + 4) {
      return;
    }

    const messageBuffer = readBuffer.subarray(4, messageLength + 4);
    readBuffer = readBuffer.subarray(messageLength + 4);

    try {
      const message = JSON.parse(messageBuffer.toString("utf8"));
      handleNativeMessage(message);
    } catch (error) {
      writeLog("Invalid native message", error);
    }
  }
}

function handleNativeMessage(message) {
  if (!message || typeof message !== "object") {
    return;
  }

  if (message.type === "render.video") {
    void runRenderJob(message).catch((error) => {
      sendError(message.requestId, error);
      void cleanupJob(message.requestId);
    });
    return;
  }

  if (message.type === "renderer.ping") {
    void resolveBundledFfmpeg()
      .then(() => {
        sendMessage({
          requestId: message.requestId,
          type: "renderer.pong"
        });
      })
      .catch((error) => sendError(message.requestId, error));
    return;
  }

  if (message.type === "render.release" || message.type === "render.cancel") {
    void cleanupJob(message.requestId);
  }
}

async function runRenderJob(message) {
  const requestId = typeof message.requestId === "string" ? message.requestId : "";
  const payload = message.payload;

  if (!requestId) {
    throw new NativeRenderError("INVALID_REQUEST", "Missing render request id.");
  }

  if (activeJobs.has(requestId)) {
    throw new NativeRenderError("INVALID_REQUEST", "A render job with this id is already running.");
  }

  validatePayload(payload);

  const ffmpegPath = await resolveBundledFfmpeg();
  const tempDir = await mkdtemp(path.join(tmpdir(), "quoti-renderer-"));
  const templatePath = path.join(tempDir, "template.png");
  const outputPath = path.join(tempDir, "output.mp4");
  const job = {
    ffmpeg: null,
    outputPath,
    releaseTimer: null,
    requestId,
    server: null,
    tempDir
  };

  activeJobs.set(requestId, job);

  await writeFile(templatePath, Buffer.from(payload.template.dataBase64, "base64"));

  sendProgress(requestId, "loading-renderer", "Native renderer ready", 0.05);

  let lastError = null;

  for (let index = 0; index < payload.candidates.length; index += 1) {
    const candidate = payload.candidates[index];

    try {
      sendProgress(
        requestId,
        "preparing-media",
        payload.candidates.length > 1 ? `Native media source ${index + 1}/${payload.candidates.length}` : "Preparing native media",
        0.08
      );

      await renderCandidate({
        candidate,
        ffmpegPath,
        job,
        outputPath,
        quality: payload.quality,
        template: payload.template,
        templatePath
      });

      lastError = null;
      break;
    } catch (error) {
      lastError = error;
      await rm(outputPath, { force: true });
    }
  }

  if (lastError) {
    throw lastError;
  }

  await assertFileExists(outputPath);

  sendProgress(requestId, "finalizing", "Preparing native download", 0.98);

  const download = await createDownloadServer(job, outputPath);
  job.server = download.server;
  job.releaseTimer = setTimeout(() => {
    void cleanupJob(requestId);
  }, requestTimeoutMs);

  sendMessage({
    requestId,
    result: {
      downloadUrl: download.url,
      filenameExtension: "mp4",
      mimeType: "video/mp4"
    },
    type: "render.ready"
  });
}

async function renderCandidate({ candidate, ffmpegPath, job, outputPath, quality, template, templatePath }) {
  const filter = buildVideoFilter(template);
  const preset = getQualityPreset(quality);
  const args = [
    "-y",
    "-hide_banner",
    "-i",
    candidate,
    "-loop",
    "1",
    "-i",
    templatePath,
    "-filter_complex",
    filter,
    "-map",
    "[v]",
    "-map",
    "0:a?",
    "-c:v",
    "libx264",
    "-preset",
    preset.preset,
    "-crf",
    String(preset.crf),
    "-c:a",
    "aac",
    "-b:a",
    "128k",
    "-movflags",
    "+faststart",
    "-shortest",
    outputPath
  ];

  sendProgress(job.requestId, "rendering", "Rendering with native FFmpeg", 0.1);

  await new Promise((resolve, reject) => {
    let stderr = "";
    let durationSeconds = 0;
    let lastProgress = 0.1;

    const ffmpeg = spawn(ffmpegPath, args, {
      stdio: ["ignore", "ignore", "pipe"],
      windowsHide: true
    });

    job.ffmpeg = ffmpeg;

    ffmpeg.stderr.setEncoding("utf8");
    ffmpeg.stderr.on("data", (chunk) => {
      stderr += chunk;
      durationSeconds = parseDurationSeconds(stderr) || durationSeconds;

      const timeSeconds = parseLastTimeSeconds(chunk);

      if (durationSeconds > 0 && timeSeconds > 0) {
        const progress = Math.min(0.96, 0.1 + (timeSeconds / durationSeconds) * 0.84);

        if (progress - lastProgress >= 0.01) {
          lastProgress = progress;
          sendProgress(job.requestId, "rendering", "Rendering with native FFmpeg", progress);
        }
      }

      if (stderr.length > 16000) {
        stderr = stderr.slice(-12000);
      }
    });

    ffmpeg.on("error", (error) => {
      job.ffmpeg = null;
      reject(new NativeRenderError("RENDERER_UNAVAILABLE", "Native FFmpeg could not be started.", error));
    });

    ffmpeg.on("close", (code) => {
      job.ffmpeg = null;

      if (code === 0) {
        sendProgress(job.requestId, "rendering", "Rendering with native FFmpeg", 0.96);
        resolve();
        return;
      }

      reject(new NativeRenderError("FFMPEG_FAILED", formatFfmpegFailure(code, stderr)));
    });
  });
}

function buildVideoFilter(template) {
  const { height, width, x, y } = template.mediaRect;
  const canvasWidth = makeEven(template.width);
  const canvasHeight = makeEven(template.height);
  const sourceFilter = template.sourceCrop ? `crop=${buildSourceCropExpression(template.sourceCrop)},` : "";

  return [
    `[0:v]${sourceFilter}scale=${width}:${height}:force_original_aspect_ratio=increase,crop=${width}:${height},setsar=1[media]`,
    `color=c=black@0:s=${canvasWidth}x${canvasHeight},format=rgba[base]`,
    `[base][media]overlay=${x}:${y}:shortest=1[media_canvas]`,
    `[media_canvas][1:v]overlay=0:0:shortest=1,crop=${canvasWidth}:${canvasHeight},format=yuv420p[v]`
  ].join(";");
}

function buildSourceCropExpression(crop) {
  return [
    `w=trunc(iw*${formatCropValue(crop.width)}/2)*2`,
    `h=trunc(ih*${formatCropValue(crop.height)}/2)*2`,
    `x=trunc(iw*${formatCropValue(crop.x)}/2)*2`,
    `y=trunc(ih*${formatCropValue(crop.y)}/2)*2`
  ].join(":");
}

function formatCropValue(value) {
  const numericValue = Number(value);

  return Math.max(0, Math.min(1, Number.isFinite(numericValue) ? numericValue : 0)).toFixed(6);
}

function getQualityPreset(quality) {
  if (quality === "fast") {
    return {
      crf: 23,
      preset: "ultrafast"
    };
  }

  if (quality === "high") {
    return {
      crf: 18,
      preset: "veryfast"
    };
  }

  return {
    crf: 19,
    preset: "veryfast"
  };
}

async function createDownloadServer(job, outputPath) {
  const fileStats = await stat(outputPath);
  const routePath = `/quoti-render/${encodeURIComponent(job.requestId)}.mp4`;
  const server = createServer((request, response) => {
    const requestUrl = new URL(request.url ?? "/", "http://127.0.0.1");

    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
    response.setHeader("Access-Control-Allow-Headers", "Content-Type");

    if (request.method === "OPTIONS") {
      response.writeHead(204);
      response.end();
      return;
    }

    if (requestUrl.pathname !== routePath || (request.method !== "GET" && request.method !== "HEAD")) {
      response.writeHead(404);
      response.end("Not found");
      return;
    }

    response.writeHead(200, {
      "Cache-Control": "no-store",
      "Content-Length": String(fileStats.size),
      "Content-Type": "video/mp4"
    });

    if (request.method === "HEAD") {
      response.end();
      return;
    }

    createReadStream(outputPath).pipe(response);
  });

  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => resolve());
  });

  const address = server.address();

  if (!address || typeof address === "string") {
    server.close();
    throw new NativeRenderError("RENDERER_UNAVAILABLE", "Could not allocate a native download port.");
  }

  return {
    server,
    url: `http://127.0.0.1:${address.port}${routePath}`
  };
}

async function resolveBundledFfmpeg() {
  const platformArch = `${process.platform}-${process.arch}`;
  const binaryName = process.platform === "win32" ? "ffmpeg.exe" : "ffmpeg";
  const candidates = [
    path.join(hostRoot, "vendor", "ffmpeg", platformArch, binaryName),
    path.join(hostRoot, "vendor", "ffmpeg", binaryName),
    process.env.QUOTI_FFMPEG_PATH
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (await fileExists(candidate)) {
      return candidate;
    }
  }

  throw new NativeRenderError(
    "FFMPEG_NOT_BUNDLED",
    `Bundled FFmpeg was not found. Expected ${path.join("vendor", "ffmpeg", platformArch, binaryName)}.`
  );
}

function validatePayload(payload) {
  if (!payload || typeof payload !== "object") {
    throw new NativeRenderError("INVALID_REQUEST", "Missing native render payload.");
  }

  if (!Array.isArray(payload.candidates) || payload.candidates.length === 0) {
    throw new NativeRenderError("MEDIA_SOURCE_UNAVAILABLE", "No native media source candidates were provided.");
  }

  for (const candidate of payload.candidates) {
    if (typeof candidate !== "string" || !/^https?:\/\//i.test(candidate)) {
      throw new NativeRenderError("MEDIA_SOURCE_UNAVAILABLE", "Native media source candidates must be HTTP URLs.");
    }
  }

  if (!payload.template || typeof payload.template.dataBase64 !== "string") {
    throw new NativeRenderError("INVALID_REQUEST", "Missing native template PNG.");
  }
}

async function assertFileExists(filePath) {
  const fileStats = await stat(filePath);

  if (!fileStats.isFile() || fileStats.size === 0) {
    throw new NativeRenderError("FFMPEG_FAILED", "Native FFmpeg did not produce an MP4 file.");
  }
}

async function cleanupJob(requestId) {
  const job = activeJobs.get(requestId);

  if (!job) {
    return;
  }

  activeJobs.delete(requestId);

  if (job.releaseTimer) {
    clearTimeout(job.releaseTimer);
  }

  if (job.ffmpeg) {
    job.ffmpeg.kill("SIGTERM");
    job.ffmpeg = null;
  }

  if (job.server) {
    await new Promise((resolve) => job.server.close(() => resolve()));
    job.server = null;
  }

  if (job.tempDir) {
    await rm(job.tempDir, {
      force: true,
      recursive: true
    });
  }
}

async function cleanupAll() {
  await Promise.all([...activeJobs.keys()].map((requestId) => cleanupJob(requestId)));
}

async function fileExists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

function parseDurationSeconds(value) {
  const match = /Duration:\s*(\d{2}):(\d{2}):(\d{2}(?:\.\d+)?)/.exec(value);

  return match ? toSeconds(match[1], match[2], match[3]) : 0;
}

function parseLastTimeSeconds(value) {
  const matches = [...value.matchAll(/time=(\d{2}):(\d{2}):(\d{2}(?:\.\d+)?)/g)];
  const match = matches.at(-1);

  return match ? toSeconds(match[1], match[2], match[3]) : 0;
}

function toSeconds(hours, minutes, seconds) {
  return Number(hours) * 3600 + Number(minutes) * 60 + Number(seconds);
}

function makeEven(value) {
  return Math.max(2, Math.floor(Number(value) / 2) * 2);
}

function formatFfmpegFailure(code, stderr) {
  const lines = stderr
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(-8)
    .join(" ");

  const detail = lines.length > 900 ? `${lines.slice(0, 897)}...` : lines;

  return detail ? `Native FFmpeg exited with code ${code}. ${detail}` : `Native FFmpeg exited with code ${code}.`;
}

function sendProgress(requestId, stage, message, progress) {
  sendMessage({
    message,
    progress,
    requestId,
    stage,
    type: "render.progress"
  });
}

function sendError(requestId, error) {
  const nativeError = error instanceof NativeRenderError
    ? error
    : new NativeRenderError("FFMPEG_FAILED", "Native rendering failed.", error);

  sendMessage({
    error: {
      code: nativeError.code,
      message: nativeError.message
    },
    requestId,
    type: "render.error"
  });

  writeLog(nativeError.message, nativeError.cause);
}

function sendMessage(message) {
  const body = Buffer.from(JSON.stringify(message), "utf8");
  const header = Buffer.alloc(4);
  header.writeUInt32LE(body.length, 0);
  process.stdout.write(Buffer.concat([header, body]));
}

function writeLog(message, error) {
  const suffix = error instanceof Error ? ` ${error.stack ?? error.message}` : "";
  process.stderr.write(`[Quoti Native] ${message}${suffix}\n`);
}
