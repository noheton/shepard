package de.dlr.shepard.context.references.videostreamreference.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VID1a — unit tests for {@link VideoProbeService#parseProbeResult(String)}
 * and the helper methods. Does NOT invoke a real ffprobe process.
 */
class VideoProbeServiceTest {

  VideoProbeService service;

  @BeforeEach
  void setUp() {
    service = new VideoProbeService();
    service.objectMapper = new ObjectMapper();
  }

  // ── parseProbeResult ──────────────────────────────────────────────────────

  @Test
  void parseProbeResult_fullMp4WithCreationTime() {
    // Typical ffprobe output for an H.264/AAC MP4 with a creation_time tag.
    String json = """
        {
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "h264",
              "width": 1920,
              "height": 1080,
              "r_frame_rate": "30/1"
            },
            {
              "codec_type": "audio",
              "codec_name": "aac"
            }
          ],
          "format": {
            "duration": "42.5",
            "size": "1048576",
            "tags": {
              "creation_time": "2024-03-15T10:30:00.000000Z"
            }
          }
        }
        """;

    VideoProbeResult r = service.parseProbeResult(json);

    assertThat(r.durationSeconds()).isEqualTo(42.5);
    assertThat(r.fileSizeBytes()).isEqualTo(1_048_576L);
    assertThat(r.width()).isEqualTo(1920);
    assertThat(r.height()).isEqualTo(1080);
    assertThat(r.frameRate()).isEqualTo(30.0);
    assertThat(r.videoCodec()).isEqualTo("h264");
    assertThat(r.audioCodec()).isEqualTo("aac");
    // 2024-03-15T10:30:00Z → epoch seconds = 1710498600, nanos = 0
    assertThat(r.wallClockTimestamp()).isEqualTo(1_710_498_600L * 1_000_000_000L);
  }

  @Test
  void parseProbeResult_ntscFrameRate() {
    // 29.97 fps → 30000/1001
    String json = """
        {
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "h264",
              "width": 1280,
              "height": 720,
              "r_frame_rate": "30000/1001"
            }
          ],
          "format": {
            "duration": "10.0",
            "size": "512000"
          }
        }
        """;

    VideoProbeResult r = service.parseProbeResult(json);
    assertThat(r.frameRate()).isCloseTo(29.97, org.assertj.core.data.Offset.offset(0.01));
    assertThat(r.audioCodec()).isNull();
    assertThat(r.wallClockTimestamp()).isNull();
  }

  @Test
  void parseProbeResult_noCreationTime_wallClockNull() {
    String json = """
        {
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "vp9",
              "width": 640,
              "height": 360,
              "r_frame_rate": "25/1"
            }
          ],
          "format": {
            "duration": "5.0",
            "size": "200000",
            "tags": {}
          }
        }
        """;

    VideoProbeResult r = service.parseProbeResult(json);
    assertThat(r.videoCodec()).isEqualTo("vp9");
    assertThat(r.wallClockTimestamp()).isNull();
  }

  @Test
  void parseProbeResult_noAudioStream() {
    String json = """
        {
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "hevc",
              "width": 3840,
              "height": 2160,
              "r_frame_rate": "60/1"
            }
          ],
          "format": {
            "duration": "120.0",
            "size": "5000000"
          }
        }
        """;

    VideoProbeResult r = service.parseProbeResult(json);
    assertThat(r.audioCodec()).isNull();
    assertThat(r.videoCodec()).isEqualTo("hevc");
    assertThat(r.frameRate()).isEqualTo(60.0);
  }

  @Test
  void parseProbeResult_emptyJson_returnsAllNulls() {
    VideoProbeResult r = service.parseProbeResult("{}");
    assertThat(r.durationSeconds()).isNull();
    assertThat(r.fileSizeBytes()).isNull();
    assertThat(r.width()).isNull();
    assertThat(r.videoCodec()).isNull();
  }

  @Test
  void parseProbeResult_nullJson_returnsEmpty() {
    VideoProbeResult r = service.parseProbeResult(null);
    assertThat(r).isEqualTo(VideoProbeResult.empty());
  }

  @Test
  void parseProbeResult_blankJson_returnsEmpty() {
    VideoProbeResult r = service.parseProbeResult("   ");
    assertThat(r).isEqualTo(VideoProbeResult.empty());
  }

  @Test
  void parseProbeResult_invalidJson_returnsEmpty() {
    VideoProbeResult r = service.parseProbeResult("{not valid json");
    assertThat(r).isEqualTo(VideoProbeResult.empty());
  }

  // ── parseFrameRate ────────────────────────────────────────────────────────

  @Test
  void parseFrameRate_fraction_30over1() {
    assertThat(service.parseFrameRate("30/1")).isEqualTo(30.0);
  }

  @Test
  void parseFrameRate_fraction_ntsc() {
    assertThat(service.parseFrameRate("30000/1001")).isCloseTo(29.97, org.assertj.core.data.Offset.offset(0.01));
  }

  @Test
  void parseFrameRate_plainDecimal() {
    assertThat(service.parseFrameRate("25.0")).isEqualTo(25.0);
  }

  @Test
  void parseFrameRate_null_returnsNull() {
    assertThat(service.parseFrameRate(null)).isNull();
  }

  @Test
  void parseFrameRate_blank_returnsNull() {
    assertThat(service.parseFrameRate("  ")).isNull();
  }

  @Test
  void parseFrameRate_zeroDenominator_returnsNull() {
    assertThat(service.parseFrameRate("30/0")).isNull();
  }

  @Test
  void parseFrameRate_garbage_returnsNull() {
    assertThat(service.parseFrameRate("fps")).isNull();
  }

  // ── parseCreationTime ─────────────────────────────────────────────────────

  @Test
  void parseCreationTime_utcTimestamp() {
    // 2024-03-15T10:30:00.000000Z
    Long nanos = service.parseCreationTime("2024-03-15T10:30:00.000000Z");
    assertThat(nanos).isNotNull();
    // 2024-03-15T10:30:00Z = 1710498600 seconds since epoch
    assertThat(nanos).isEqualTo(1_710_498_600L * 1_000_000_000L);
  }

  @Test
  void parseCreationTime_subSecond() {
    // 2024-03-15T10:30:00.123456Z = 1710498600.123456 seconds
    Long nanos = service.parseCreationTime("2024-03-15T10:30:00.123456Z");
    assertThat(nanos).isNotNull();
    // epoch seconds = 1710498600, nano-of-second = 123_456_000
    assertThat(nanos).isEqualTo(1_710_498_600L * 1_000_000_000L + 123_456_000L);
  }

  @Test
  void parseCreationTime_null_returnsNull() {
    assertThat(service.parseCreationTime(null)).isNull();
  }

  @Test
  void parseCreationTime_blank_returnsNull() {
    assertThat(service.parseCreationTime("")).isNull();
  }

  @Test
  void parseCreationTime_invalidFormat_returnsNull() {
    assertThat(service.parseCreationTime("not-a-date")).isNull();
  }
}
