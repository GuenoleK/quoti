Place the production-bundled `ffmpeg.exe` binary in this directory for local runs.

Do not commit the binary to Git. It should be provided by the release package or installer, alongside the required FFmpeg license/source notices.

The native helper checks this path before any diagnostic override and does not use a system `ffmpeg` from `PATH`.
