package de.dlr.shepard.v2.fair.services;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.videostreamreference.VideoPayload;
import de.dlr.shepard.v2.fair.io.DmpSnippetIO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * FAIR7 — Pure projection service: assembles a copy-paste-ready Markdown DMP
 * block from the existing FAIR-relevant fields on a {@link Collection} and its
 * {@link DataObject}s.
 *
 * <p>No new entities, no side effects, no Neo4j writes. This bean is
 * intentionally stateless so unit tests can call {@code generate()} without
 * any mocking.
 *
 * <h3>Field resolution rules</h3>
 * <ul>
 *   <li>{@code license} / {@code accessRights} / {@code description} — read
 *       directly from the {@link Collection} (which extends
 *       {@link de.dlr.shepard.common.neo4j.entities.AbstractDataObject}; the
 *       fields are stored on the {@code :Collection} Neo4j node).</li>
 *   <li>{@code embargoEndDate} — collected from all DataObjects; the
 *       <em>latest</em> (lexicographically greatest) ISO-8601 date is used so
 *       the DMP statement is conservative (reports the longest-running
 *       embargo).</li>
 *   <li>{@code createdByOrcid} — unique ORCIDs aggregated across all
 *       DataObjects (not the Collection itself, since the ORCID stamp is
 *       per-DataObject).</li>
 *   <li>{@code pid} — the {@code :Collection} entity does not currently carry
 *       a PID field (PIDs are minted by the separate
 *       {@link de.dlr.shepard.publish.entities.Publication} workflow and are not
 *       reachable via this lightweight projection). The snippet always emits
 *       "no PID assigned" in this version; PID lookup is future work
 *       (see {@code aidocs/16} FAIR7 row).</li>
 * </ul>
 */
@ApplicationScoped
public class DmpSnippetService {

  /**
   * Generate a {@link DmpSnippetIO} from a fully-loaded {@link Collection}.
   *
   * <p>The caller is responsible for ensuring the Collection was loaded with
   * its DataObject relationships populated (the standard
   * {@code CollectionService.getCollectionWithDataObjectsAndIncomingReferences}
   * load depth is sufficient).
   *
   * @param collection the fully-loaded Collection
   * @return a {@link DmpSnippetIO} with the snippet Markdown and missing-fields list
   */
  public DmpSnippetIO generate(Collection collection) {
    List<DataObject> dataObjects = collection.getDataObjects() != null
      ? collection.getDataObjects()
      : List.of();

    // ── Aggregate FAIR fields from DataObjects ────────────────────────────
    Set<String> orcids = new LinkedHashSet<>();
    String latestEmbargoEndDate = null;
    for (DataObject dobj : dataObjects) {
      String orcid = dobj.getCreatedByOrcid();
      if (orcid != null && !orcid.isBlank()) {
        orcids.add(orcid.trim());
      }
      String embargo = dobj.getEmbargoEndDate();
      if (embargo != null && !embargo.isBlank()) {
        if (latestEmbargoEndDate == null || embargo.compareTo(latestEmbargoEndDate) > 0) {
          latestEmbargoEndDate = embargo;
        }
      }
    }

    // ── Payload-kind detection from reference types ───────────────────────
    Set<String> kinds = new LinkedHashSet<>();
    for (DataObject dobj : dataObjects) {
      if (dobj.getReferences() == null) continue;
      for (BasicReference ref : dobj.getReferences()) {
        if (ref instanceof TimeseriesReference) kinds.add("timeseries");
        else if (ref instanceof FileBundleReference) kinds.add("files");
        else if (ref instanceof StructuredDataReference) kinds.add("structured data");
        else if (ref.getClass().isAnnotationPresent(VideoPayload.class)) kinds.add("video streams");
      }
    }

    // ── FAIR field values (Collection-level) ─────────────────────────────
    String collAppId = collection.getAppId() != null ? collection.getAppId() : "unknown";
    String collName = collection.getName() != null ? collection.getName() : "(unnamed)";
    String license = collection.getLicense();
    String accessRights = collection.getAccessRights();
    String description = collection.getDescription();
    int doCount = dataObjects.size();
    String kindsStr = kinds.isEmpty() ? "unknown" : String.join(", ", kinds);
    String orcidStr = orcids.isEmpty() ? "not specified" : String.join(", ", orcids);
    String embargoStr = latestEmbargoEndDate != null ? latestEmbargoEndDate : "no embargo";

    // ── Missing-fields detection ──────────────────────────────────────────
    List<String> missingFields = new ArrayList<>();
    if (license == null || license.isBlank()) missingFields.add("license");
    if (accessRights == null || accessRights.isBlank()) missingFields.add("accessRights");
    if (orcids.isEmpty()) missingFields.add("orcid");
    if (description == null || description.isBlank()) missingFields.add("description");

    // ── Snippet building ──────────────────────────────────────────────────
    String licenseDisplay = (license != null && !license.isBlank())
      ? license
      : "not specified — set via PATCH /v2/collections/" + collAppId;
    String accessDisplay = (accessRights != null && !accessRights.isBlank())
      ? accessRights
      : "not specified";
    String descDisplay = (description != null && !description.isBlank())
      ? description
      : "no description";

    StringBuilder sb = new StringBuilder();
    sb.append("## Data Management Plan — ").append(collName).append("\n\n");
    sb.append("**Dataset identifier:** `").append(collAppId).append("` (no PID assigned)\n");
    sb.append("**License:** ").append(licenseDisplay).append("\n");
    sb.append("**Access rights:** ").append(accessDisplay).append("\n");
    sb.append("**Embargo end date:** ").append(embargoStr).append("\n");
    sb.append("**Creator ORCID(s):** ").append(orcidStr).append("\n");
    sb.append("**Number of DataObjects:** ").append(doCount).append("\n");
    sb.append("**Payload kinds present:** ").append(kindsStr).append("\n");
    sb.append("**Description:** ").append(descDisplay).append("\n");
    sb.append("\n");
    sb.append("### Recommended DMP statements\n\n");

    // Data description
    sb.append("> **Data description:** This dataset contains ").append(doCount)
      .append(" research data object").append(doCount == 1 ? "" : "s")
      .append("\n> managed in the Shepard research data management platform.")
      .append(" Payload types include: ").append(kindsStr).append(".\n\n");

    // Findability
    sb.append("> **Findability:** The dataset is identified by the unique identifier `")
      .append(collAppId).append("`.\n")
      .append("> No persistent identifier (PID) has been assigned yet.\n\n");

    // Accessibility
    sb.append("> **Accessibility:** The dataset is stored in the Shepard RDM system.\n")
      .append("> Access is controlled via role-based permissions (Read/Write/Manager).\n");
    if ("OPEN".equalsIgnoreCase(accessRights)) {
      sb.append("> The dataset is publicly accessible.\n\n");
    } else {
      sb.append("> Access is restricted.\n\n");
    }

    // Interoperability
    sb.append("> **Interoperability:** Metadata follows the FAIR data principles.\n")
      .append("> Semantic annotations are expressed using W3C RDF vocabulary terms.\n\n");

    // Reusability
    sb.append("> **Reusability:** ");
    if (license != null && !license.isBlank()) {
      sb.append("Licensed under ").append(license).append(".\n");
    } else {
      sb.append("No license has been assigned. Assign one via PATCH /v2/collections/")
        .append(collAppId).append(".\n");
    }
    sb.append("> Provenance is captured automatically via the built-in Provenance system.\n");

    String snippet = sb.toString();

    return new DmpSnippetIO(collAppId, collName, snippet, missingFields);
  }
}
