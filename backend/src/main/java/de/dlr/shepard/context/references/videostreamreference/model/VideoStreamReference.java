package de.dlr.shepard.context.references.videostreamreference.model;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;

/**
 * VID1a — {@code :VideoStreamReference} Neo4j node entity.
 *
 * <p>Holds metadata for a video file attached to a {@link
 * de.dlr.shepard.context.collection.entities.DataObject}. The bytes
 * live in the active {@link de.dlr.shepard.storage.FileStorage} adapter
 * (GridFS or S3) and are addressed by {@link #storageLocator}.
 *
 * <p>{@link #wallClockTimestamp} is the temporal anchor connecting
 * the video to the TM1 time-reference model on
 * {@link de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference}.
 *
 * <p>Note: This feature lands in-tree for VID1a to lean on the existing
 * FileStorage SPI + permission gate. Plugin extraction is post-VID1-series
 * work (per CLAUDE.md plugin-first rule §"New payload kinds").
 */
@NodeEntity
@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class VideoStreamReference extends BasicReference {

  /**
   * Opaque storage locator in the format {@code "<providerId>:<locator>"}
   * (mirrors {@link de.dlr.shepard.storage.StorageLocator}). Null until
   * the upload is committed to the storage backend. Stored as a single
   * Neo4j string property to avoid coupling the entity shape to the SPI
   * record type.
   */
  private String storageLocator;

  /** MIME type of the video file, e.g. {@code video/mp4}, {@code video/quicktime}. */
  private String mimeType;

  /** Size of the video file in bytes. Set from Content-Length or ffprobe {@code format.size}. */
  private Long fileSizeBytes;

  /** Duration in seconds extracted by ffprobe from {@code format.duration}. Null if not available. */
  private Double durationSeconds;

  /** Pixel width of the video stream. Null if unknown. */
  private Integer width;

  /** Pixel height of the video stream. Null if unknown. */
  private Integer height;

  /** Frames per second. Parsed from ffprobe {@code r_frame_rate} (e.g. {@code "30/1"}). Null if unknown. */
  private Double frameRate;

  /** Video codec name, e.g. {@code h264}, {@code vp9}. Null if unknown. */
  private String videoCodec;

  /** Audio codec name, e.g. {@code aac}, {@code opus}. Null if unknown. */
  private String audioCodec;

  /**
   * Wall-clock timestamp as nanoseconds since the Unix epoch (UTC). Extracted
   * from ffprobe's {@code format.tags.creation_time} ISO-8601 tag. Null if
   * the tag is absent or unparseable.
   *
   * <p>This is the temporal anchor that connects the video to the TM1
   * time-reference model — it identifies the absolute wall-clock start of
   * the recording and can be used to synchronise video with
   * {@link de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference}
   * data that carries a matching {@code wallClockOffset}.
   */
  private Long wallClockTimestamp;

  /** For testing purposes only. */
  public VideoStreamReference(long id) {
    super(id);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(
      storageLocator, mimeType, fileSizeBytes, durationSeconds,
      width, height, frameRate, videoCodec, audioCodec, wallClockTimestamp
    );
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof VideoStreamReference)) return false;
    VideoStreamReference other = (VideoStreamReference) obj;
    return (
      Objects.equals(storageLocator, other.storageLocator) &&
      Objects.equals(mimeType, other.mimeType) &&
      Objects.equals(fileSizeBytes, other.fileSizeBytes) &&
      Objects.equals(durationSeconds, other.durationSeconds) &&
      Objects.equals(width, other.width) &&
      Objects.equals(height, other.height) &&
      Objects.equals(frameRate, other.frameRate) &&
      Objects.equals(videoCodec, other.videoCodec) &&
      Objects.equals(audioCodec, other.audioCodec) &&
      Objects.equals(wallClockTimestamp, other.wallClockTimestamp)
    );
  }
}
