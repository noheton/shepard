package de.dlr.shepard.v2.dataobject.io;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * REF-1 — enriched single-DataObject response for
 * {@code GET /v2/collections/{cid}/data-objects/{did}}.
 *
 * <p>Extends the upstream-frozen {@link DataObjectIO} (which MUST NOT be
 * modified per the API-version policy) with typed per-kind container lists
 * and compact summary objects for related DataObjects.
 *
 * <p>Legacy fields from {@link DataObjectIO} ({@code referenceIds[]},
 * {@code successorIds[]}, etc.) are retained for backward compatibility.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(
  name = "DataObjectDetail",
  description = "Full DataObject detail response, extends DataObjectIO with typed " +
    "container lists and compact predecessor/successor/parent/child summaries."
)
public class DataObjectDetailV2IO extends DataObjectIO {

  /**
   * Typed breakdown of the containers attached to this DataObject,
   * split by kind.
   */
  @Schema(readOnly = true, description = "Typed container lists by kind.")
  private Containers containers = new Containers();

  /**
   * Compact summaries of all direct predecessors (appId, id, name, status).
   */
  @Schema(readOnly = true, description = "Direct predecessors of this DataObject.")
  private List<DataObjectSummaryIO> predecessorSummaries = new ArrayList<>();

  /**
   * Compact summaries of all direct successors (appId, id, name, status).
   */
  @Schema(readOnly = true, description = "Direct successors of this DataObject.")
  private List<DataObjectSummaryIO> successorSummaries = new ArrayList<>();

  /**
   * Compact summaries of all direct children (appId, id, name, status).
   */
  @Schema(readOnly = true, description = "Direct children of this DataObject.")
  private List<DataObjectSummaryIO> childSummaries = new ArrayList<>();

  /**
   * Compact summary of the parent DataObject, or {@code null} if this is a
   * top-level DataObject.
   */
  @Schema(readOnly = true, nullable = true, description = "Parent DataObject summary, or null if top-level.")
  private DataObjectSummaryIO parentSummary;

  public DataObjectDetailV2IO(DataObject dataObject) {
    super(dataObject);

    // Build typed container lists from the reference list already loaded
    // by DataObjectIO's constructor (which walks dataObject.getReferences()).
    List<ContainerRefIO> timeseries = new ArrayList<>();
    List<ContainerRefIO> files = new ArrayList<>();
    List<ContainerRefIO> structuredData = new ArrayList<>();

    for (BasicReference ref : dataObject.getReferences()) {
      if (ref.isDeleted()) continue;

      long refId = ref.getShepardId() != null ? ref.getShepardId() : (ref.getId() != null ? ref.getId() : -1L);
      String refAppId = ref.getAppId();

      if (ref instanceof TimeseriesReference tr) {
        if (tr.getTimeseriesContainer() != null) {
          long cId = tr.getTimeseriesContainer().getId() != null ? tr.getTimeseriesContainer().getId() : -1L;
          timeseries.add(new ContainerRefIO(
            tr.getTimeseriesContainer().getAppId(),
            tr.getTimeseriesContainer().getName(),
            cId,
            refId,
            refAppId
          ));
        }
      } else if (ref instanceof FileBundleReference fbr) {
        if (fbr.getFileContainer() != null) {
          long cId = fbr.getFileContainer().getId() != null ? fbr.getFileContainer().getId() : -1L;
          files.add(new ContainerRefIO(
            fbr.getFileContainer().getAppId(),
            fbr.getFileContainer().getName(),
            cId,
            refId,
            refAppId
          ));
        }
      } else if (ref instanceof StructuredDataReference sdr) {
        if (sdr.getStructuredDataContainer() != null) {
          long cId = sdr.getStructuredDataContainer().getId() != null ? sdr.getStructuredDataContainer().getId() : -1L;
          structuredData.add(new ContainerRefIO(
            sdr.getStructuredDataContainer().getAppId(),
            sdr.getStructuredDataContainer().getName(),
            cId,
            refId,
            refAppId
          ));
        }
      }
    }

    this.containers = new Containers(timeseries, files, structuredData);

    // Predecessor / successor / child summaries.
    for (DataObject p : dataObject.getPredecessors()) {
      if (!p.isDeleted()) predecessorSummaries.add(new DataObjectSummaryIO(p));
    }
    for (DataObject s : dataObject.getSuccessors()) {
      if (!s.isDeleted()) successorSummaries.add(new DataObjectSummaryIO(s));
    }
    for (DataObject c : dataObject.getChildren()) {
      if (!c.isDeleted()) childSummaries.add(new DataObjectSummaryIO(c));
    }
    if (dataObject.getParent() != null && !dataObject.getParent().isDeleted()) {
      this.parentSummary = new DataObjectSummaryIO(dataObject.getParent());
    }
  }

  // ── inner class ──────────────────────────────────────────────────────────

  /**
   * Typed container lists for timeseries, file, and structured-data kinds.
   */
  @Data
  @NoArgsConstructor
  @Schema(name = "DataObjectContainers", description = "Per-kind container references.")
  public static class Containers {

    @Schema(readOnly = true, description = "Timeseries container references (one per TimeseriesReference).")
    private List<ContainerRefIO> timeseries = new ArrayList<>();

    @Schema(readOnly = true, description = "File container references (one per FileBundleReference).")
    private List<ContainerRefIO> files = new ArrayList<>();

    @Schema(readOnly = true, description = "Structured-data container references (one per StructuredDataReference).")
    private List<ContainerRefIO> structuredData = new ArrayList<>();

    public Containers(List<ContainerRefIO> timeseries, List<ContainerRefIO> files, List<ContainerRefIO> structuredData) {
      this.timeseries = timeseries;
      this.files = files;
      this.structuredData = structuredData;
    }
  }
}
