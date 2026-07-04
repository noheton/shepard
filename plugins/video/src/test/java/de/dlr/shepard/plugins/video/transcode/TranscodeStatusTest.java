package de.dlr.shepard.plugins.video.transcode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — unit tests for {@link TranscodeStatus}.
 */
class TranscodeStatusTest {

  @Test
  void fromString_null_or_blank_returns_NONE() {
    assertThat(TranscodeStatus.fromString(null)).isEqualTo(TranscodeStatus.NONE);
    assertThat(TranscodeStatus.fromString("")).isEqualTo(TranscodeStatus.NONE);
    assertThat(TranscodeStatus.fromString("   ")).isEqualTo(TranscodeStatus.NONE);
  }

  @Test
  void fromString_recognises_canonical_values_case_insensitively() {
    assertThat(TranscodeStatus.fromString("PENDING")).isEqualTo(TranscodeStatus.PENDING);
    assertThat(TranscodeStatus.fromString("pending")).isEqualTo(TranscodeStatus.PENDING);
    assertThat(TranscodeStatus.fromString("Ready")).isEqualTo(TranscodeStatus.READY);
    assertThat(TranscodeStatus.fromString("FAILED")).isEqualTo(TranscodeStatus.FAILED);
  }

  @Test
  void fromString_strips_whitespace() {
    assertThat(TranscodeStatus.fromString("  READY  ")).isEqualTo(TranscodeStatus.READY);
  }

  @Test
  void fromString_unknown_value_falls_back_to_NONE() {
    // Forward-compat: a newer node persisted "QUEUED" — older reader treats as NONE.
    assertThat(TranscodeStatus.fromString("QUEUED")).isEqualTo(TranscodeStatus.NONE);
    assertThat(TranscodeStatus.fromString("???")).isEqualTo(TranscodeStatus.NONE);
  }
}
