package de.dlr.shepard.v2.importer.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.importer.entities.ImportPlan;
import de.dlr.shepard.v2.importer.io.ImportJobResultIO;
import de.dlr.shepard.v2.importer.io.ImportJobResultIO.CreatedEntityIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO.ManifestContainerIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO.ManifestDataObjectIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IMP2 — business logic for executing a validated import plan.
 *
 * <p>Consumes a stored {@link ImportPlan} that was minted by
 * {@link ImportValidationService} and creates all DataObjects, Containers, and
 * wires parent/predecessor relationships declared in the embedded manifest.
 *
 * <h2>Execution strategy</h2>
 * <pre>
 *   Pass 1 — create DataObjects without parent/predecessor links.
 *             Builds a {@code Map<localRef, shepardId>} for the link pass.
 *   Pass 2 — update each DataObject to wire parent + predecessorIds.
 *             {@code successorIds} is deliberately left {@code null} to avoid
 *             the strict-equality guard in {@link DataObjectService#updateDataObject}.
 *   Containers — create FILE / TIMESERIES / STRUCTURED_DATA containers.
 *   References — NOT wired in IMP2 (manifest carries only localRefs, not payload
 *                OIDs).  A warning is appended to the result for any reference row
 *                declared in the manifest.  Reference wiring is deferred to IMP3.
 * </pre>
 *
 * <h2>Failure handling</h2>
 * <p>Failures in Pass 2 or container creation are caught individually; the
 * execution continues and the result is reported as {@code PARTIAL_FAILURE}.
 * Pass 1 failures abort the creation of the affected DataObject only (other
 * DataObjects in the manifest can still succeed).
 *
 * <p>This service is {@link RequestScoped} because it delegates to
 * {@link DataObjectService} and the container services which are themselves
 * {@code @RequestScoped}.
 */
@RequestScoped
public class ImportExecutionService {

  @Inject
  ObjectMapper objectMapper;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  // ─── Public API ───────────────────────────────────────────────────────────

  /**
   * Execute the import manifest stored in the given plan.
   *
   * <p>The plan must have {@code status = VALID} and a non-null {@code manifestJson}.
   * Callers are responsible for all pre-flight checks (expiry, fingerprint, lock)
   * before invoking this method.
   *
   * @param plan               the validated plan to execute
   * @param collectionShepardId the Neo4j internal id of the target Collection
   * @return the job result record (never null; contains status + created entities + errors)
   */
  public ImportJobResultIO execute(ImportPlan plan, long collectionShepardId) {
    String jobAppId = AppIdGenerator.next();
    List<CreatedEntityIO> dataObjects = new ArrayList<>();
    List<CreatedEntityIO> containers = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    // ── Deserialise manifest ────────────────────────────────────────────────
    ImportManifestIO manifest;
    try {
      manifest = objectMapper.readValue(plan.getManifestJson(), ImportManifestIO.class);
    } catch (JsonProcessingException e) {
      Log.errorf("IMP2: failed to deserialise manifestJson for plan %s: %s",
        plan.getCommitId(), e.getMessage());
      errors.add("Internal error: could not deserialise stored manifest — " + e.getMessage());
      return new ImportJobResultIO(jobAppId, plan.getCommitId(), "PARTIAL_FAILURE",
        dataObjects, containers, errors);
    }

    // ── Pass 1: create DataObjects (no parent / predecessor links yet) ──────
    // Map from localRef → shepardId (the Neo4j internal id used for linking).
    Map<String, Long> refToShepardId = new HashMap<>();
    // Map from localRef → appId (UUID v7 for the result payload).
    Map<String, String> refToAppId = new HashMap<>();

    for (ManifestDataObjectIO dSpec : manifest.dataObjects()) {
      try {
        DataObjectIO io = new DataObjectIO();
        io.setName(dSpec.name());
        io.setDescription(dSpec.description());
        io.setStatus(dSpec.status());
        io.setAttributes(dSpec.attributes() != null ? dSpec.attributes() : Map.of());
        // Pass 1: no parent/predecessor links — they require other DOs to exist first.

        var created = dataObjectService.createDataObject(collectionShepardId, io);
        refToShepardId.put(dSpec.localRef(), created.getShepardId());
        refToAppId.put(dSpec.localRef(), created.getAppId());
        dataObjects.add(new CreatedEntityIO(dSpec.localRef(), created.getAppId(), "DataObject"));
        Log.debugf("IMP2: created DataObject localRef=%s appId=%s", dSpec.localRef(), created.getAppId());
      } catch (Exception e) {
        Log.warnf("IMP2: failed to create DataObject localRef=%s: %s", dSpec.localRef(), e.getMessage());
        errors.add("Failed to create DataObject '" + dSpec.localRef() + "': " + e.getMessage());
        dataObjects.add(new CreatedEntityIO(dSpec.localRef(), null, "DataObject"));
      }
    }

    // ── Pass 2: wire parent + predecessor links ────────────────────────────
    for (ManifestDataObjectIO dSpec : manifest.dataObjects()) {
      Long shepardId = refToShepardId.get(dSpec.localRef());
      if (shepardId == null) {
        // Pass 1 failed for this DO — skip link wiring.
        continue;
      }
      boolean needsUpdate = (dSpec.parentRef() != null)
        || (dSpec.predecessorRefs() != null && !dSpec.predecessorRefs().isEmpty());
      if (!needsUpdate) continue;

      try {
        // Reconstruct the IO with link data only.
        DataObjectIO io = new DataObjectIO();
        io.setName(dSpec.name());  // updateDataObject requires a non-blank name.
        io.setDescription(dSpec.description());
        io.setStatus(dSpec.status());
        io.setAttributes(dSpec.attributes() != null ? dSpec.attributes() : Map.of());
        // successorIds MUST be null (not new long[0]) to avoid the strict-equality
        // guard in DataObjectService.updateDataObject (lines 333-339).
        // io.setSuccessorIds(null) is the default — no call needed.

        // Parent
        if (dSpec.parentRef() != null) {
          Long parentShepardId = refToShepardId.get(dSpec.parentRef());
          if (parentShepardId != null) {
            io.setParentId(parentShepardId);
          } else {
            errors.add("DataObject '" + dSpec.localRef() + "' parentRef '" +
              dSpec.parentRef() + "' was not created — skipping parent link");
          }
        }

        // Predecessors
        if (dSpec.predecessorRefs() != null && !dSpec.predecessorRefs().isEmpty()) {
          long[] predIds = dSpec.predecessorRefs().stream()
            .filter(ref -> refToShepardId.containsKey(ref))
            .mapToLong(ref -> refToShepardId.get(ref))
            .toArray();
          io.setPredecessorIds(predIds);

          // Warn about any refs that failed in Pass 1.
          dSpec.predecessorRefs().stream()
            .filter(ref -> !refToShepardId.containsKey(ref))
            .forEach(ref -> errors.add(
              "DataObject '" + dSpec.localRef() + "' predecessorRef '" + ref +
              "' was not created — skipping predecessor link"));
        }

        dataObjectService.updateDataObject(collectionShepardId, shepardId, io);
        Log.debugf("IMP2: wired links for DataObject localRef=%s", dSpec.localRef());
      } catch (Exception e) {
        Log.warnf("IMP2: failed to wire links for DataObject localRef=%s: %s",
          dSpec.localRef(), e.getMessage());
        errors.add("Failed to wire links for DataObject '" + dSpec.localRef() +
          "': " + e.getMessage());
      }
    }

    // ── Containers ────────────────────────────────────────────────────────
    if (manifest.containers() != null) {
      for (ManifestContainerIO cSpec : manifest.containers()) {
        try {
          String containerAppId = createContainer(cSpec);
          containers.add(new CreatedEntityIO(cSpec.localRef(), containerAppId, kindLabel(cSpec.type())));
          Log.debugf("IMP2: created container localRef=%s type=%s appId=%s",
            cSpec.localRef(), cSpec.type(), containerAppId);
        } catch (Exception e) {
          Log.warnf("IMP2: failed to create container localRef=%s type=%s: %s",
            cSpec.localRef(), cSpec.type(), e.getMessage());
          errors.add("Failed to create container '" + cSpec.localRef() +
            "' (type=" + cSpec.type() + "): " + e.getMessage());
          containers.add(new CreatedEntityIO(cSpec.localRef(), null, kindLabel(cSpec.type())));
        }
      }
    }

    // ── References ────────────────────────────────────────────────────────
    // IMP2 limitation: ManifestReferenceIO only carries localRefs — it does NOT
    // carry payload OIDs (fileOids, structuredDataOids) or timeseries 5-tuples
    // (measurement/device/location/symbolicName/field + start/end).  All three
    // reference services require non-null, non-empty payload data.
    //
    // Reference wiring is deferred to IMP3 when the manifest IO is extended
    // to carry payload-level identifiers.  We note the count in the result
    // warnings so callers are informed.
    if (manifest.references() != null && !manifest.references().isEmpty()) {
      errors.add("IMP2 limitation: " + manifest.references().size() +
        " reference row(s) declared in manifest were NOT wired — reference " +
        "attachment requires payload OIDs not yet carried by the manifest. " +
        "Wire references manually or wait for IMP3.");
    }

    String status = errors.isEmpty() ? "COMPLETED" : "PARTIAL_FAILURE";
    return new ImportJobResultIO(jobAppId, plan.getCommitId(), status,
      dataObjects, containers, errors);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  /**
   * Create a container of the specified type and return its appId.
   *
   * @param cSpec the manifest container specification
   * @return the appId of the newly created container
   */
  private String createContainer(ManifestContainerIO cSpec) {
    return switch (cSpec.type()) {
      case "FILE" -> {
        FileContainerIO io = new FileContainerIO();
        io.setName(cSpec.name());
        yield fileContainerService.createContainer(io).getAppId();
      }
      case "TIMESERIES" -> {
        TimeseriesContainerIO io = new TimeseriesContainerIO();
        io.setName(cSpec.name());
        yield timeseriesContainerService.createContainer(io).getAppId();
      }
      case "STRUCTURED_DATA" -> {
        StructuredDataContainerIO io = new StructuredDataContainerIO();
        io.setName(cSpec.name());
        yield structuredDataContainerService.createContainer(io).getAppId();
      }
      default -> throw new IllegalArgumentException(
        "Unknown container type: " + cSpec.type()
      );
    };
  }

  /** Map manifest container type string to the kind label used in {@link CreatedEntityIO}. */
  private static String kindLabel(String type) {
    return switch (type) {
      case "FILE" -> "FileContainer";
      case "TIMESERIES" -> "TimeseriesContainer";
      case "STRUCTURED_DATA" -> "StructuredDataContainer";
      default -> type;
    };
  }
}
