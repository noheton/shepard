package de.dlr.shepard.context.snapshot.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2e — wire shape for the response of the snapshot diff endpoint
 * ({@code GET /v2/snapshots/{aAppId}/diff/{bAppId}}).
 *
 * <p>The diff compares the {@link de.dlr.shepard.context.snapshot.entities.SnapshotEntry}
 * sets of two snapshots (A = base, B = head) and classifies every entity as:
 * <ul>
 *   <li>{@code added} — present in B but not in A (created between the snapshots).</li>
 *   <li>{@code removed} — present in A but not in B (deleted or out of scope in B).</li>
 *   <li>{@code changed} — present in both with a different {@code revision} value.</li>
 *   <li>{@code unchangedCount} — present in both with the same {@code revision}
 *       (count only; the set can be arbitrarily large).</li>
 * </ul>
 *
 * <p>All lists are sorted by {@code entityAppId} ascending for deterministic,
 * diff-friendly output. When the diff exceeds the {@code ?maxItems} cap, each list
 * is capped at {@code ceil(maxItems/3)} entries and {@code truncated} is set to
 * {@code true}. The {@code totalAdded}, {@code totalRemoved}, and
 * {@code totalChanged} counts always reflect the full uncapped diff.
 *
 * <p>Cross-references: {@code aidocs/41} §7; {@code aidocs/16} V2e,
 * {@code APISIMP-SNAPSHOT-DIFF-UNCAPPED}.
 */
@Schema(name = "SnapshotDiff")
public record SnapshotDiffIO(
  @Schema(description = "appId of the base snapshot (A).")
  String snapshotAAppId,

  @Schema(description = "appId of the head snapshot (B).")
  String snapshotBAppId,

  @Schema(description = "Epoch milliseconds at which snapshot A was captured.")
  long snapshotACapturedAtMs,

  @Schema(description = "Epoch milliseconds at which snapshot B was captured.")
  long snapshotBCapturedAtMs,

  @Schema(description = "entityAppIds present in B but not in A (created between snapshots). Sorted ascending. Capped at ceil(?maxItems/3) entries when truncated=true.")
  List<String> added,

  @Schema(description = "entityAppIds present in A but not in B (deleted or out of scope in B). Sorted ascending. Capped at ceil(?maxItems/3) entries when truncated=true.")
  List<String> removed,

  @Schema(description = "Entities present in both snapshots with different revision values. Sorted by entityAppId ascending. Capped at ceil(?maxItems/3) entries when truncated=true.")
  List<DiffEntry> changed,

  @Schema(description = "Count of entities present in both snapshots with identical revisions (not listed to avoid large payloads).")
  int unchangedCount,

  @Schema(description = "Total count of added entities in the full diff (before capping). Always accurate even when truncated=true.")
  int totalAdded,

  @Schema(description = "Total count of removed entities in the full diff (before capping). Always accurate even when truncated=true.")
  int totalRemoved,

  @Schema(description = "Total count of changed entities in the full diff (before capping). Always accurate even when truncated=true.")
  int totalChanged,

  @Schema(description = "True when the diff exceeded the ?maxItems cap and one or more lists were truncated. Use totalAdded/totalRemoved/totalChanged to know the full scope.")
  boolean truncated
) {

  /**
   * One changed entity: the same {@code entityAppId} appears in both snapshots
   * but with different {@code revision} values.
   */
  @Schema(name = "SnapshotDiffEntry")
  public record DiffEntry(
    @Schema(description = "appId of the entity that changed revision between snapshots.")
    String entityAppId,

    @Schema(description = "Revision the entity held in snapshot A (base).")
    long revisionA,

    @Schema(description = "Revision the entity held in snapshot B (head).")
    long revisionB
  ) {}
}
