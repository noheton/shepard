package de.dlr.shepard.plugins.video.transcode;

import de.dlr.shepard.spi.transcode.TranscodeRequest;
import de.dlr.shepard.spi.transcode.TranscodeResult;
import de.dlr.shepard.spi.transcode.TranscodingProvider;
import de.dlr.shepard.spi.transcode.TranscodingProviderRegistry;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StoragePutRequest;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — default {@link TranscodingProvider}
 * implementation: shells out to the {@code ffmpeg} binary on the backend
 * container's {@code PATH}.
 *
 * <p>The backend image already installs ffmpeg (Dockerfile lines 45-65,
 * BtbN static build) so this provider is a one-line {@code ProcessBuilder}
 * invocation — no sidecar, no network IPC, no operator install step.
 * Mirrors the {@link de.dlr.shepard.context.references.videostreamreference.services.VideoProbeService}
 * pattern line-for-line: temp-file in, process, temp-file out, fail-soft on
 * missing binary / non-zero exit / timeout.
 *
 * <p>This is the <b>local</b> half of the local/remote provider split
 * mandated by {@code CLAUDE.md} "Always: ship a working local default for
 * every AI capability". A future {@code RemoteFfmpegTranscodingProvider}
 * could call out to a queue / dedicated transcode node / NVENC GPU host
 * — the SPI shape doesn't change.
 *
 * <h2>ffmpeg invocation profile</h2>
 *
 * <p>The codec profile is fixed for v1: h.264 baseline / yuv420p / AAC /
 * {@code +faststart} (so the browser's {@code <video>} can start playing
 * before the file finishes downloading). Bitrate is parameterised via
 * {@link TranscodeRequest#videoBitrateKbps()}. Optional letterbox-safe
 * downscale via {@link TranscodeRequest#maxHeight()} — set to 0 to
 * preserve source resolution. Audio is re-encoded to AAC 128k.
 *
 * <p>Verbatim command (placeholders in braces):
 *
 * <pre>{@code
 * ffmpeg -y -i {source.tmp} -c:v libx264 -profile:v baseline \
 *   -pix_fmt yuv420p -preset veryfast -b:v {N}k -maxrate {N*1.5}k -bufsize {N*2}k \
 *   [-vf scale=-2:{H}] \
 *   -c:a aac -b:a 128k -movflags +faststart {target.mp4}
 * }</pre>
 *
 * <h2>Configurability</h2>
 *
 * <p>{@code shepard.plugins.video.ffmpeg.binary} (default {@code "ffmpeg"})
 * overrides the binary name / path — useful when an operator drops a
 * patched build into {@code /usr/local/bin/ffmpeg-nvenc} or similar.
 *
 * @see TranscodingProvider
 * @see TranscodingProviderRegistry
 */
@ApplicationScoped
public class LocalFfmpegTranscodingProvider implements TranscodingProvider {

  /** Provider id matching {@link TranscodingProviderRegistry#DEFAULT_PROVIDER_ID}. */
  public static final String ID = "ffmpeg-local";

  @ConfigProperty(name = "shepard.plugins.video.ffmpeg.binary", defaultValue = "ffmpeg")
  String ffmpegBinary;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public TranscodeResult transcode(TranscodeRequest request, FileStorage storage) throws StorageException {
    if (storage == null) {
      throw new IllegalArgumentException("storage must not be null");
    }
    Path sourceTmp = null;
    Path targetTmp = null;
    try {
      sourceTmp = Files.createTempFile("shepard-vid-transcode-src-", ".tmp");
      targetTmp = Files.createTempFile("shepard-vid-transcode-out-", ".mp4");
      // Materialise the source from storage onto disk so ffmpeg can seek.
      StorageGetResponse src = storage.get(request.sourceLocator());
      try (InputStream in = src.stream()) {
        Files.copy(in, sourceTmp, StandardCopyOption.REPLACE_EXISTING);
      }
      // Run ffmpeg.
      RunOutcome outcome = runFfmpeg(sourceTmp, targetTmp, request);
      if (!outcome.success) {
        return TranscodeResult.failed(outcome.message);
      }
      // Push the proxy back into the SAME storage adapter so callers get a
      // round-tripped StorageLocator (no-magic-routes rule).
      long size = Files.size(targetTmp);
      try (InputStream proxyIn = Files.newInputStream(targetTmp)) {
        StoragePutRequest put = new StoragePutRequest(
          request.targetContainer(),
          request.targetFileName(),
          "video/mp4",
          proxyIn,
          size,
          null
        );
        StorageLocator proxyLocator = storage.put(put);
        Log.infof(
          "VID-FFMPEG-TRANSCODE: produced proxy %s (%d bytes) from source %s",
          proxyLocator, size, request.sourceLocator()
        );
        return TranscodeResult.ok(proxyLocator);
      }
    } catch (IOException ex) {
      Log.warnf(ex, "VID-FFMPEG-TRANSCODE: temp-file I/O error transcoding %s", request.sourceLocator());
      return TranscodeResult.failed("temp-file I/O: " + ex.getMessage());
    } finally {
      cleanup(sourceTmp);
      cleanup(targetTmp);
    }
  }

  /**
   * Build the ffmpeg command vector. Package-private so unit tests can
   * assert the produced flags without invoking a real process.
   */
  List<String> buildCommand(Path source, Path target, TranscodeRequest req) {
    int bitrate = req.videoBitrateKbps() > 0 ? req.videoBitrateKbps() : TranscodeRequest.DEFAULT_BITRATE_KBPS;
    long maxrate = Math.round(bitrate * 1.5);
    long bufsize = bitrate * 2L;
    List<String> cmd = new ArrayList<>();
    cmd.add(ffmpegBinary);
    cmd.add("-y");
    cmd.add("-nostdin");
    cmd.add("-i");
    cmd.add(source.toAbsolutePath().toString());
    cmd.add("-c:v");
    cmd.add("libx264");
    cmd.add("-profile:v");
    cmd.add("baseline");
    cmd.add("-level");
    cmd.add("4.0");
    cmd.add("-pix_fmt");
    cmd.add("yuv420p");
    cmd.add("-preset");
    cmd.add("veryfast");
    cmd.add("-b:v");
    cmd.add(bitrate + "k");
    cmd.add("-maxrate");
    cmd.add(maxrate + "k");
    cmd.add("-bufsize");
    cmd.add(bufsize + "k");
    if (req.maxHeight() > 0) {
      cmd.add("-vf");
      // Letterbox-safe: -2 = even width auto-derived from aspect ratio.
      cmd.add("scale=-2:'min(" + req.maxHeight() + ",ih)'");
    }
    cmd.add("-c:a");
    cmd.add("aac");
    cmd.add("-b:a");
    cmd.add("128k");
    cmd.add("-movflags");
    cmd.add("+faststart");
    cmd.add(target.toAbsolutePath().toString());
    return cmd;
  }

  /** Spawn the process and wait for it; package-private for tests. */
  RunOutcome runFfmpeg(Path source, Path target, TranscodeRequest req) {
    List<String> cmd = buildCommand(source, target, req);
    Process process;
    try {
      process = new ProcessBuilder(cmd)
        .redirectErrorStream(true)
        .start();
    } catch (IOException ex) {
      Log.warnf("VID-FFMPEG-TRANSCODE: %s not on PATH or failed to start — %s", ffmpegBinary, ex.getMessage());
      return RunOutcome.fail("ffmpeg-binary-missing: " + ex.getMessage());
    }
    String tail;
    try {
      // Drain stdout+stderr; bounded buffer (last ~64 KiB) so a verbose
      // ffmpeg run doesn't OOM us.
      tail = drainTail(process.getInputStream(), 64 * 1024);
      boolean exited = process.waitFor(req.timeoutSeconds(), TimeUnit.SECONDS);
      if (!exited) {
        process.destroyForcibly();
        Log.warnf("VID-FFMPEG-TRANSCODE: timed out after %ds", req.timeoutSeconds());
        return RunOutcome.fail("timeout after " + req.timeoutSeconds() + "s");
      }
      int exit = process.exitValue();
      if (exit != 0) {
        Log.warnf("VID-FFMPEG-TRANSCODE: ffmpeg exited %d; tail=%s", exit, tail);
        return RunOutcome.fail("ffmpeg exit " + exit + ": " + tail);
      }
      return RunOutcome.ok();
    } catch (IOException | InterruptedException ex) {
      Thread.currentThread().interrupt();
      Log.warnf(ex, "VID-FFMPEG-TRANSCODE: error reading ffmpeg output");
      return RunOutcome.fail("io: " + ex.getMessage());
    }
  }

  /** Read up to {@code maxBytes} into a string for diagnostic tailing. */
  static String drainTail(InputStream in, int maxBytes) throws IOException {
    byte[] buf = new byte[8192];
    StringBuilder sb = new StringBuilder(maxBytes);
    int n;
    while ((n = in.read(buf)) != -1) {
      int room = maxBytes - sb.length();
      if (room <= 0) {
        // Keep draining to let ffmpeg finish; just stop appending.
        continue;
      }
      sb.append(new String(buf, 0, Math.min(n, room)));
    }
    return sb.toString();
  }

  private static void cleanup(Path p) {
    if (p == null) return;
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignored) {
      Log.warnf("VID-FFMPEG-TRANSCODE: could not delete temp file %s", p);
    }
  }

  /** Internal outcome shape — package-private for tests. */
  record RunOutcome(boolean success, String message) {
    static RunOutcome ok() { return new RunOutcome(true, null); }
    static RunOutcome fail(String msg) { return new RunOutcome(false, msg); }
  }
}
