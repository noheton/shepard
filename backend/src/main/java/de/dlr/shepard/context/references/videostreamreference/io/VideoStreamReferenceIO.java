package de.dlr.shepard.context.references.videostreamreference.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * VID1a wire shape for {@link VideoStreamReference}.
 *
 * <p>ffprobe-extracted fields ({@code durationSeconds}, {@code width},
 * {@code height}, {@code frameRate}, {@code videoCodec}, {@code audioCodec},
 * {@code wallClockTimestamp}) are read-only on the wire — clients cannot
 * set them; the probe overwrites them on upload.
 */
@Data
@NoArgsConstructor
@Schema(name = "VideoStreamReference")
public class VideoStreamReferenceIO extends BasicReferenceIO {

  @Schema(
    description = "MIME type of the video file, e.g. video/mp4, video/quicktime.",
    example = "video/mp4"
  )
  private String mimeType;

  @Schema(
    readOnly = true,
    description = "File size in bytes."
  )
  private Long fileSizeBytes;

  @Schema(
    readOnly = true,
    description = "Duration in seconds as extracted by ffprobe. Null if extraction failed."
  )
  private Double durationSeconds;

  @Schema(
    readOnly = true,
    description = "Pixel width of the video stream. Null if unknown."
  )
  private Integer width;

  @Schema(
    readOnly = true,
    description = "Pixel height of the video stream. Null if unknown."
  )
  private Integer height;

  @Schema(
    readOnly = true,
    description = "Frames per second. Null if unknown."
  )
  private Double frameRate;

  @Schema(
    readOnly = true,
    description = "Video codec name, e.g. h264, vp9. Null if unknown."
  )
  private String videoCodec;

  @Schema(
    readOnly = true,
    description = "Audio codec name, e.g. aac, opus. Null if unknown."
  )
  private String audioCodec;

  /**
   * Wall-clock timestamp as nanoseconds since the Unix epoch (UTC).
   * Extracted from ffprobe's {@code format.tags.creation_time} ISO-8601 tag.
   * Null if the tag is absent or unparseable.
   *
   * <p>This is the temporal anchor connecting the video to the TM1
   * time-reference model on {@code TimeseriesReference}.
   */
  @Schema(
    readOnly = true,
    description = "Wall-clock timestamp as UTC nanoseconds since epoch, from ffprobe creation_time. " +
                  "Null if not present in the video file. " +
                  "TM1 temporal anchor: use to align with TimeseriesReference.wallClockOffset."
  )
  private Long wallClockTimestamp;

  public VideoStreamReferenceIO(VideoStreamReference ref) {
    super(ref);
    this.mimeType = ref.getMimeType();
    this.fileSizeBytes = ref.getFileSizeBytes();
    this.durationSeconds = ref.getDurationSeconds();
    this.width = ref.getWidth();
    this.height = ref.getHeight();
    this.frameRate = ref.getFrameRate();
    this.videoCodec = ref.getVideoCodec();
    this.audioCodec = ref.getAudioCodec();
    this.wallClockTimestamp = ref.getWallClockTimestamp();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    VideoStreamReferenceIO other = (VideoStreamReferenceIO) o;
    return (
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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(
      mimeType, fileSizeBytes, durationSeconds, width, height,
      frameRate, videoCodec, audioCodec, wallClockTimestamp
    );
    return result;
  }
}
