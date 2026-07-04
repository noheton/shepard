package de.dlr.shepard.plugins.video.transcode;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.spi.transcode.TranscodeRequest;
import de.dlr.shepard.storage.StorageLocator;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — unit tests for
 * {@link LocalFfmpegTranscodingProvider}. Avoids invoking a real ffmpeg
 * process — covers the command-vector builder + the stderr drain helper +
 * the failure-shape contract.
 */
class LocalFfmpegTranscodingProviderTest {

  private LocalFfmpegTranscodingProvider provider;

  @BeforeEach
  void setUp() {
    provider = new LocalFfmpegTranscodingProvider();
    provider.ffmpegBinary = "ffmpeg";
  }

  @Test
  void id_matches_registry_default() {
    assertThat(provider.id()).isEqualTo("ffmpeg-local");
  }

  @Test
  void buildCommand_emits_h264_baseline_yuv420p_faststart_profile() {
    var loc = new StorageLocator("gridfs", "container:oid");
    var req = TranscodeRequest.defaults(loc, "videos", "clip_proxy.mp4");
    Path source = Path.of("/tmp/src.tmp");
    Path target = Path.of("/tmp/out.mp4");

    List<String> cmd = provider.buildCommand(source, target, req);

    assertThat(cmd).containsSubsequence("ffmpeg", "-y", "-nostdin", "-i", source.toAbsolutePath().toString());
    // Codec + profile fingerprint that makes the proxy browser-friendly.
    assertThat(cmd).containsSubsequence("-c:v", "libx264", "-profile:v", "baseline");
    assertThat(cmd).containsSubsequence("-pix_fmt", "yuv420p");
    assertThat(cmd).containsSubsequence("-movflags", "+faststart");
    // Audio re-encoded to AAC for cross-browser playback.
    assertThat(cmd).containsSubsequence("-c:a", "aac", "-b:a", "128k");
    // Bitrate flags computed from the request value (3000k default).
    assertThat(cmd).contains("3000k");
    // Letterbox-safe downscale filter present for the 1080 default.
    assertThat(cmd).contains("-vf");
    assertThat(cmd).anyMatch(s -> s.startsWith("scale=-2:") && s.contains("1080"));
  }

  @Test
  void buildCommand_omits_scale_filter_when_maxHeight_zero() {
    var req = new TranscodeRequest(
      new StorageLocator("gridfs", "c:o"), "videos", "clip_proxy.mp4",
      3000, 0, 3600
    );
    List<String> cmd = provider.buildCommand(Path.of("/tmp/a"), Path.of("/tmp/b"), req);

    assertThat(cmd).doesNotContain("-vf");
    assertThat(cmd).noneMatch(s -> s.startsWith("scale="));
  }

  @Test
  void buildCommand_falls_back_to_default_bitrate_when_zero() {
    var req = new TranscodeRequest(
      new StorageLocator("gridfs", "c:o"), "videos", "clip_proxy.mp4",
      0, 1080, 3600
    );
    List<String> cmd = provider.buildCommand(Path.of("/tmp/a"), Path.of("/tmp/b"), req);

    assertThat(cmd).contains(TranscodeRequest.DEFAULT_BITRATE_KBPS + "k");
  }

  @Test
  void buildCommand_respects_configured_binary_name() {
    provider.ffmpegBinary = "/usr/local/bin/ffmpeg-nvenc";
    var req = TranscodeRequest.defaults(new StorageLocator("gridfs", "c:o"), "videos", "clip_proxy.mp4");
    List<String> cmd = provider.buildCommand(Path.of("/tmp/a"), Path.of("/tmp/b"), req);

    assertThat(cmd.get(0)).isEqualTo("/usr/local/bin/ffmpeg-nvenc");
  }

  @Test
  void drainTail_caps_output_at_max_bytes() throws Exception {
    byte[] payload = new byte[1024 * 1024]; // 1 MiB
    for (int i = 0; i < payload.length; i++) payload[i] = (byte) ('a' + (i % 26));
    String tail = LocalFfmpegTranscodingProvider.drainTail(new ByteArrayInputStream(payload), 1024);

    // We capped at 1024 bytes; the drain still consumes the remainder so
    // the process can exit, but the returned string is bounded.
    assertThat(tail.length()).isEqualTo(1024);
  }

  @Test
  void drainTail_handles_empty_stream() throws Exception {
    String tail = LocalFfmpegTranscodingProvider.drainTail(new ByteArrayInputStream(new byte[0]), 1024);
    assertThat(tail).isEmpty();
  }

  @Test
  void runOutcome_success_factory_carries_no_message() {
    var ok = LocalFfmpegTranscodingProvider.RunOutcome.ok();
    assertThat(ok.success()).isTrue();
    assertThat(ok.message()).isNull();
  }

  @Test
  void runOutcome_failure_factory_carries_the_message() {
    var fail = LocalFfmpegTranscodingProvider.RunOutcome.fail("ffmpeg exit 1");
    assertThat(fail.success()).isFalse();
    assertThat(fail.message()).isEqualTo("ffmpeg exit 1");
  }
}
