package de.dlr.shepard.v2.timeseriescontainer.io;

import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-ID PR-2 — per-channel listing shape for the {@code /v2/} surface.
 *
 * <p>This is the wire-side debut of {@code shepardId} as a single-field
 * channel identity. The 5-tuple ({@code measurement}, {@code device},
 * {@code location}, {@code symbolicName}, {@code field}) stays in the
 * response so existing callers can keep working through the transition;
 * the single-field identity is the new preferred handle.
 *
 * <p>Naming note: this IO is {@code /v2/}-only. The v1
 * {@code TimeseriesIO} stays byte-frozen — it does NOT expose
 * {@code shepardId} (locked by {@code V1WireFidelityTest}).
 */
@Schema(name = "TimeseriesChannelV2", description = "Per-channel listing entry on the /v2/ surface, addressed by shepardId.")
public record TimeseriesChannelV2IO(
  /**
   * Stable single-field channel identity (UUID). Resolves to a single
   * Postgres {@code timeseries} row via {@code TsChannelResolver}. Always
   * present on persisted rows.
   */
  @Schema(description = "Channel shepardId — the new single-field identity for this channel.", required = true)
  UUID shepardId,

  /**
   * The legacy numeric id of the row. Kept for clients that still
   * deduplicate or join against {@code TimeseriesIO.id}; will be deprecated
   * once {@code shepardId} adoption is complete.
   */
  @Schema(description = "Legacy numeric channel id (Postgres serial).", required = true)
  int id,

  /** Owning container id (Postgres FK). */
  @Schema(required = true)
  long containerId,

  @Schema(required = true)
  String measurement,

  @Schema(required = true)
  String device,

  @Schema(required = true)
  String location,

  @Schema(required = true)
  String symbolicName,

  @Schema(required = true)
  String field,

  @Schema(required = true)
  DataPointValueType valueType
) {

  /**
   * Project a Postgres row into the wire shape. {@code shepardId} is
   * carried through directly — null only if the caller hands us an
   * unpersisted entity (a programming error; persisted rows always have
   * a value by the V1.11.0 NOT NULL constraint).
   */
  public static TimeseriesChannelV2IO from(TimeseriesEntity row) {
    return new TimeseriesChannelV2IO(
      row.getShepardId(),
      row.getId(),
      row.getContainerId(),
      row.getMeasurement(),
      row.getDevice(),
      row.getLocation(),
      row.getSymbolicName(),
      row.getField(),
      row.getValueType()
    );
  }
}
