package de.dlr.shepard.v2.thermography.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * OTVIS-VIEWER — frame-index metadata for a decoded Edevis OTvis archive.
 *
 * <p>Response shape for
 * {@code GET /v2/thermography/otvis/{fileReferenceAppId}/frames}. Lists the
 * decoded frames (lock-in amplitude/phase results first, raw-calibrated
 * frames after), the available channels per frame, and the per-archive
 * dimensions. The actual pixel data is fetched per-frame as a PNG heatmap
 * via {@code GET .../frames/{n}?channel=amplitude|phase|temperature} — this
 * index stays small (JSON, no float arrays) so the viewer can build a
 * scrubber without pulling every frame's bytes.
 *
 * <p>{@code partialReason} surfaces the extractor's fail-soft tolerance
 * notes (unknown DataFormat, missing calibration, truncation) so the
 * viewer can warn the operator rather than silently drop frames.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Frame index for a decoded OTvis archive — drives the "
  + "viewer scrubber + channel toggle.")
public class OtvisFramesIO {

  @Schema(description = "Singleton FileReference appId of the .OTvis archive.")
  private String fileReferenceAppId;

  @Schema(description = "Image width in pixels (first decoded frame).")
  private int width;

  @Schema(description = "Image height in pixels (first decoded frame).")
  private int height;

  @Schema(description = "Total frame count (lock-in + raw-calibrated).")
  private int frameCount;

  @Schema(description = "Per-frame descriptors, scrubber-ordered.")
  private List<FrameInfo> frames;

  @Schema(description = "Fail-soft tolerance notes from the extractor; null "
    + "when extraction was lossless.")
  private String partialReason;

  /**
   * One decoded frame. {@code kind} is {@code "lockin"} (carries amplitude +
   * phase channels) or {@code "raw"} (carries the temperature channel).
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "One decoded frame descriptor.")
  public static class FrameInfo {

    @Schema(description = "Zero-based frame index (scrubber position).")
    private int index;

    @Schema(description = "Frame kind — `lockin` (amplitude+phase) or `raw` (temperature).")
    private String kind;

    @Schema(description = "Channels available for this frame, e.g. [amplitude, phase].")
    private List<String> channels;

    @Schema(description = "Channel to default the viewer to — `phase` for "
      + "lock-in (less sensitive to emissivity/uneven heating; the canonical "
      + "NDT defect channel), `temperature` for raw.")
    private String defaultChannel;
  }
}
