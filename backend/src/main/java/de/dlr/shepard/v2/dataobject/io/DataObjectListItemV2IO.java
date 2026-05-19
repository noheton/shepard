package de.dlr.shepard.v2.dataobject.io;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2 list-endpoint projection of a {@link DataObject}.
 *
 * <p>Extends the shared {@link DataObjectIO} (which is also used by the
 * upstream-frozen {@code /shepard/api/} surface) with three per-kind
 * reference counts. Adding those counts directly to {@link DataObjectIO}
 * would change the upstream wire shape — forbidden by the API-version
 * policy in {@code CLAUDE.md}. This subclass lives in the {@code /v2/}
 * shelf only and is only referenced by {@link
 * de.dlr.shepard.v2.dataobject.resources.DataObjectV2Rest#list}.
 *
 * <p>Counts reflect non-deleted references only, matching the
 * post-load {@code cutDeleted()} filter applied by
 * {@link de.dlr.shepard.context.collection.services.DataObjectService}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(name = "DataObjectListItemV2", description = "DataObject list item enriched with per-kind reference counts (v2).")
public class DataObjectListItemV2IO extends DataObjectIO {

  @Schema(
    readOnly = true,
    description = "Number of non-deleted TimeseriesReferences attached to this DataObject."
  )
  private long timeseriesCount;

  @Schema(
    readOnly = true,
    description = "Number of non-deleted file references (FileReference + SingletonFileReference) attached to this DataObject."
  )
  private long fileCount;

  @Schema(
    readOnly = true,
    description = "Number of non-deleted StructuredDataReferences attached to this DataObject."
  )
  private long structuredDataCount;

  /**
   * Copies all fields from {@code dataObject} (via the {@link DataObjectIO}
   * supertype constructor) and overlays the three reference counts.
   *
   * @param dataObject the loaded entity
   * @param tsCount    timeseries reference count (non-deleted)
   * @param fileCount  file reference count (non-deleted, both bundle and singleton kinds)
   * @param sdCount    structured-data reference count (non-deleted)
   */
  public DataObjectListItemV2IO(DataObject dataObject, long tsCount, long fileCount, long sdCount) {
    super(dataObject);
    this.timeseriesCount = tsCount;
    this.fileCount = fileCount;
    this.structuredDataCount = sdCount;
  }
}
