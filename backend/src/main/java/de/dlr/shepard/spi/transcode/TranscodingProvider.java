package de.dlr.shepard.spi.transcode;

import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageLocator;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — SPI contract for transcoding a stored
 * video payload into a browser-friendly proxy (h.264 baseline / yuv420p
 * MP4 with {@code +faststart}).
 *
 * <p>Discovered via CDI {@code @Any Instance<TranscodingProvider>} through
 * {@link TranscodingProviderRegistry}. Mirrors the local/remote provider
 * pattern from {@code CLAUDE.md} "Always: ship a working local default for
 * every AI capability": the bundled default ({@code "ffmpeg-local"}) shells
 * out to {@code ffmpeg} on the backend container's PATH (the Dockerfile
 * already installs the BtbN static build for {@code ffprobe} — the same
 * binary serves the transcoder). A future {@code "ffmpeg-remote"} provider
 * could call out to a sidecar / queue / NVENC GPU host without touching
 * callers.
 *
 * <h2>Invocation contract</h2>
 *
 * <ul>
 *   <li>The caller stages the source bytes in the active {@link FileStorage}
 *       adapter and passes the resulting {@link StorageLocator} as
 *       {@code sourceLocator}.
 *   <li>The provider reads the source via the registry's loader (it does
 *       NOT need to know the storage adapter — the registry hands it an
 *       {@link java.io.InputStream}), runs the transcode, and writes the
 *       result back through the SAME storage adapter under the proxy
 *       container key.
 *   <li>The returned {@link TranscodeResult} carries the new
 *       {@link StorageLocator}; the caller stamps it onto the entity's
 *       {@code proxyStorageLocator} field.
 * </ul>
 *
 * <h2>Failure posture</h2>
 *
 * <p>Per the {@code CLAUDE.md} "Always: secondary writes are fire-and-forget"
 * rule, transcoding is a secondary effect: a failed transcode MUST NOT
 * propagate to the upload caller. The provider returns
 * {@link TranscodeResult#failed(String)} on any unrecoverable error; the
 * dispatcher logs WARN and stamps {@code proxyStatus=FAILED} on the
 * entity so the UI can fall back to the original bytes.
 *
 * <h2>Registry shape</h2>
 *
 * <p>One provider per {@link #id()} (duplicate-id detection at registry
 * startup logs a WARN and keeps the first registration). The active
 * provider is selected by the {@code shepard.plugins.video.transcoder}
 * config key (default {@code "ffmpeg-local"}).
 *
 * @see TranscodingProviderRegistry
 * @see TranscodeRequest
 * @see TranscodeResult
 */
public interface TranscodingProvider {

  /**
   * Stable identifier for this provider — used to select the active
   * provider via {@code shepard.plugins.video.transcoder} and for
   * registry duplicate detection.
   *
   * <p>By convention: {@code "ffmpeg-local"} for the in-container
   * default, {@code "ffmpeg-remote"} for a future HTTP-sidecar
   * adapter, {@code "nvenc-local"} for a GPU-host build.
   *
   * @return non-null, non-blank id
   */
  String id();

  /**
   * Run the transcode. Implementations MUST honour the
   * {@link TranscodeRequest#timeoutSeconds()} budget and return
   * {@link TranscodeResult#failed(String)} rather than throwing on
   * recoverable failures (process timeout, unsupported source codec,
   * disk full mid-encode). Throwing is reserved for programmer errors
   * — null storage adapter, missing source locator, etc.
   *
   * @param request the transcode invocation (source locator, target
   *                container, target object key, profile knobs); never
   *                null
   * @param storage the active {@link FileStorage} adapter both reads
   *                from (the source) and writes to (the proxy)
   * @return the transcode outcome — success carries the new
   *         {@link StorageLocator}, failure carries a message
   * @throws StorageException only when the source or sink storage adapter
   *                          itself fails (the secondary-writes contract
   *                          allows the caller to swallow this)
   */
  TranscodeResult transcode(TranscodeRequest request, FileStorage storage) throws StorageException;
}
