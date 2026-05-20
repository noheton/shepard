package de.dlr.shepard.context.references.videostreamreference.io;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import org.junit.jupiter.api.Test;

/**
 * VID1a — unit tests for {@link VideoStreamReferenceIO} constructor mapping.
 */
class VideoStreamReferenceIOTest {

  private static VideoStreamReference buildRef() {
    DataObject parent = new DataObject();
    parent.setId(10L);
    parent.setShepardId(10L);
    parent.setAppId("do-app-id");

    VideoStreamReference ref = new VideoStreamReference();
    ref.setId(1L);
    ref.setShepardId(1L);
    ref.setAppId("ref-app-id");
    ref.setName("test-video");
    ref.setDataObject(parent);
    ref.setMimeType("video/mp4");
    ref.setFileSizeBytes(1_048_576L);
    ref.setDurationSeconds(42.5);
    ref.setWidth(1920);
    ref.setHeight(1080);
    ref.setFrameRate(30.0);
    ref.setVideoCodec("h264");
    ref.setAudioCodec("aac");
    ref.setWallClockTimestamp(1_710_495_000_000_000_000L);
    return ref;
  }

  @Test
  void constructor_mapsAllFields() {
    VideoStreamReference ref = buildRef();
    VideoStreamReferenceIO io = new VideoStreamReferenceIO(ref);

    assertThat(io.getAppId()).isEqualTo("ref-app-id");
    assertThat(io.getName()).isEqualTo("test-video");
    assertThat(io.getMimeType()).isEqualTo("video/mp4");
    assertThat(io.getFileSizeBytes()).isEqualTo(1_048_576L);
    assertThat(io.getDurationSeconds()).isEqualTo(42.5);
    assertThat(io.getWidth()).isEqualTo(1920);
    assertThat(io.getHeight()).isEqualTo(1080);
    assertThat(io.getFrameRate()).isEqualTo(30.0);
    assertThat(io.getVideoCodec()).isEqualTo("h264");
    assertThat(io.getAudioCodec()).isEqualTo("aac");
    assertThat(io.getWallClockTimestamp()).isEqualTo(1_710_495_000_000_000_000L);
  }

  @Test
  void constructor_handlesNullProbeFields() {
    DataObject parent = new DataObject();
    parent.setId(10L);
    parent.setShepardId(10L);

    VideoStreamReference ref = new VideoStreamReference();
    ref.setId(2L);
    ref.setShepardId(2L);
    ref.setAppId("ref-null-probe");
    ref.setName("no-probe");
    ref.setDataObject(parent);
    // All probe fields null.

    VideoStreamReferenceIO io = new VideoStreamReferenceIO(ref);

    assertThat(io.getDurationSeconds()).isNull();
    assertThat(io.getWidth()).isNull();
    assertThat(io.getHeight()).isNull();
    assertThat(io.getFrameRate()).isNull();
    assertThat(io.getVideoCodec()).isNull();
    assertThat(io.getAudioCodec()).isNull();
    assertThat(io.getWallClockTimestamp()).isNull();
    assertThat(io.getFileSizeBytes()).isNull();
  }

  @Test
  void equals_symmetricForSameContent() {
    VideoStreamReference ref = buildRef();
    VideoStreamReferenceIO a = new VideoStreamReferenceIO(ref);
    VideoStreamReferenceIO b = new VideoStreamReferenceIO(ref);
    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void noArgConstructor_yields_emptyObject() {
    VideoStreamReferenceIO io = new VideoStreamReferenceIO();
    assertThat(io.getName()).isNull();
    assertThat(io.getMimeType()).isNull();
  }
}
