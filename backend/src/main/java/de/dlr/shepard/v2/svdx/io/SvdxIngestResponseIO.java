package de.dlr.shepard.v2.svdx.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response body for {@code POST /v2/svdx/ingest}.
 *
 * <p>{@code idempotentReplay} is {@code true} when the call short-circuited
 * because the same {@code svdxFileAppId} + {@code csvFileAppId} pair had
 * already been ingested for this DataObject; the returned counts then
 * reflect the prior ingest, not the no-op replay.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SvdxIngestResponse")
public class SvdxIngestResponseIO {

  @Schema(description = "appId of the (new or existing) TimeseriesReference.")
  private String timeseriesReferenceAppId;

  @Schema(description = "Numeric Shepard id of the TimeseriesReference (for v1 cross-callers).")
  private long timeseriesReferenceShepardId;

  @Schema(description = "appId of the TimeseriesContainer the channels were ingested into.")
  private String timeseriesContainerAppId;

  @Schema(description = "Count of channels that received at least one data point.")
  private int channelCount;

  @Schema(description = "Number of rows the CSV carried (per-channel sample count, max across channels).")
  private int rowCount;

  @Schema(description = "Count of channels whose name was not present in the SVDX manifest. Annotated as `unmatched=true` on the reference.")
  private int unmatchedChannelCount;

  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  @Schema(description = "True when this call short-circuited because the same SVDX+CSV pair had already been ingested.")
  private boolean idempotentReplay;
}
