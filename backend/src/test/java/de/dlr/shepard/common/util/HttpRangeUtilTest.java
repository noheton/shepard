package de.dlr.shepard.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/**
 * MFFD-VIDEOREF-SCALE-1 — tests for {@link HttpRangeUtil}.
 */
class HttpRangeUtilTest {

  @Test
  void parseRange_nullHeader_returnsNull() {
    assertThat(HttpRangeUtil.parseRange(null, 1024)).isNull();
  }

  @Test
  void parseRange_nonByteUnit_returnsNull() {
    // Anything other than "bytes=" is not the simple-range form.
    assertThat(HttpRangeUtil.parseRange("items=0-10", 1024)).isNull();
  }

  @Test
  void parseRange_simpleRange_returnsBounds() {
    long[] r = HttpRangeUtil.parseRange("bytes=0-1023", 4096);
    assertThat(r).isNotNull();
    assertThat(r).containsExactly(0L, 1023L);
  }

  @Test
  void parseRange_openEnded_clampsToTotalMinusOne() {
    long[] r = HttpRangeUtil.parseRange("bytes=512-", 4096);
    assertThat(r).isNotNull();
    assertThat(r).containsExactly(512L, 4095L);
  }

  @Test
  void parseRange_endBeyondTotal_clampsEnd() {
    long[] r = HttpRangeUtil.parseRange("bytes=0-99999", 1024);
    assertThat(r).isNotNull();
    assertThat(r).containsExactly(0L, 1023L);
  }

  @Test
  void parseRange_startBeyondTotal_returnsNull() {
    // Caller must translate null → 416.
    assertThat(HttpRangeUtil.parseRange("bytes=2000-", 1024)).isNull();
  }

  @Test
  void parseRange_startEqualsTotal_returnsNull() {
    assertThat(HttpRangeUtil.parseRange("bytes=1024-", 1024)).isNull();
  }

  @Test
  void parseRange_negativeStart_returnsNull() {
    assertThat(HttpRangeUtil.parseRange("bytes=-50-100", 1024)).isNull();
  }

  @Test
  void parseRange_endBeforeStart_returnsNull() {
    assertThat(HttpRangeUtil.parseRange("bytes=200-100", 1024)).isNull();
  }

  @Test
  void parseRange_multiRange_returnsNull() {
    // We refuse comma-separated multi-ranges by design.
    assertThat(HttpRangeUtil.parseRange("bytes=0-100,200-300", 1024)).isNull();
  }

  @Test
  void parseRange_suffixRange_returnsNull() {
    // "bytes=-N" (last N bytes) is RFC-legal but rare in practice. We refuse.
    assertThat(HttpRangeUtil.parseRange("bytes=-100", 1024)).isNull();
  }

  @Test
  void parseRange_malformedNumber_returnsNull() {
    assertThat(HttpRangeUtil.parseRange("bytes=abc-def", 1024)).isNull();
  }

  @Test
  void parseRange_zeroTotal_returnsNull() {
    // Cannot satisfy any range against an empty resource.
    assertThat(HttpRangeUtil.parseRange("bytes=0-10", 0)).isNull();
  }

  @Test
  void sliceStream_emitsExactlyTheRequestedSlice() throws Exception {
    byte[] source = new byte[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    StreamingOutput out = HttpRangeUtil.sliceStream(new ByteArrayInputStream(source), 3L, 4L);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    out.write(sink);
    assertThat(sink.toByteArray()).containsExactly(3, 4, 5, 6);
  }

  @Test
  void sliceStream_stopsAtSourceEnd_evenWhenLengthExceedsAvailable() throws Exception {
    byte[] source = new byte[]{ 0, 1, 2 };
    StreamingOutput out = HttpRangeUtil.sliceStream(new ByteArrayInputStream(source), 1L, 100L);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    out.write(sink);
    assertThat(sink.toByteArray()).containsExactly(1, 2);
  }

  @Test
  void sliceStream_zeroLength_emitsNothing() throws Exception {
    byte[] source = new byte[]{ 0, 1, 2, 3 };
    StreamingOutput out = HttpRangeUtil.sliceStream(new ByteArrayInputStream(source), 0L, 0L);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    out.write(sink);
    assertThat(sink.toByteArray()).isEmpty();
  }
}
