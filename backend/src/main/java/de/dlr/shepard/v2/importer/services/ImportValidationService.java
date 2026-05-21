package de.dlr.shepard.v2.importer.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.importer.daos.ImportPlanDAO;
import de.dlr.shepard.v2.importer.entities.ImportPlan;
import de.dlr.shepard.v2.importer.io.ImportManifestIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO.ManifestContainerIO;
import de.dlr.shepard.v2.importer.io.ImportPlanIO.ImportSummaryIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IMP1 — business logic for import manifest dry-run validation.
 *
 * <p>The service is intentionally kept free of any write side-effects when
 * validation fails — no {@link ImportPlan} node is persisted in the error case.
 * On success, exactly one plan node is saved and its commitId returned.
 *
 * <p>Validation checks (in order):
 * <ol>
 *   <li>Collection exists.</li>
 *   <li>DataObject status values are within the allowed set.</li>
 *   <li>DataObject localRefs are unique within the manifest.</li>
 *   <li>Parent refs resolve within the manifest.</li>
 *   <li>Predecessor refs resolve within the manifest.</li>
 *   <li>Container types are within the allowed set.</li>
 *   <li>Reference rows resolve to declared DataObject and Container localRefs.</li>
 *   <li>(Warning) Names that already exist in the collection.</li>
 * </ol>
 */
@ApplicationScoped
public class ImportValidationService {

  /** Lifecycle statuses accepted on incoming DataObjects. */
  private static final Set<String> VALID_STATUSES = Set.of(
    "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"
  );

  /** Container types supported by the importer. */
  private static final Set<String> VALID_CONTAINER_TYPES = Set.of(
    "TIMESERIES", "FILE", "STRUCTURED_DATA"
  );

  /** Plan TTL in milliseconds (24 hours). */
  private static final long PLAN_TTL_MS = 86_400_000L;

  @Inject
  ImportPlanDAO importPlanDAO;

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  ObjectMapper objectMapper;

  // ─── Public API ───────────────────────────────────────────────────────────

  /**
   * Validate an import manifest.
   *
   * <p>Returns an unsaved {@link ImportPlan} with {@code status=INVALIDATED}
   * and no {@code commitId} when there are hard errors. Callers must inspect
   * the {@code summaryJson} / {@code warningsJson} / {@code status} fields.
   *
   * <p>On success a plan node is saved to Neo4j and a commitId is issued.
   *
   * @param manifest the caller-supplied manifest
   * @param username the authenticated user running the validation
   * @return the resulting plan (saved on success, unsaved on failure)
   */
  public ImportPlan validate(ImportManifestIO manifest, String username) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // 1. Collection exists?
    Optional<Long> collOgmId = collectionPropertiesDAO.findCollectionIdByAppId(
      manifest.collectionAppId());
    if (collOgmId.isEmpty()) {
      errors.add("Collection not found: " + manifest.collectionAppId());
    }

    // 2. DataObject status values
    Set<String> doRefs = new HashSet<>();
    for (var do_ : manifest.dataObjects()) {
      if (do_.status() != null && !VALID_STATUSES.contains(do_.status())) {
        errors.add(
          "Invalid status '" + do_.status() + "' on dataObject '" + do_.localRef() + "'"
        );
      }
      // 3. localRef uniqueness
      if (!doRefs.add(do_.localRef())) {
        errors.add("Duplicate dataObject localRef: " + do_.localRef());
      }
    }

    // 4. parentRef and predecessorRefs resolve within manifest
    for (var do_ : manifest.dataObjects()) {
      if (do_.parentRef() != null && !doRefs.contains(do_.parentRef())) {
        errors.add(
          "dataObject '" + do_.localRef() + "' parentRef '" + do_.parentRef() +
          "' not in manifest"
        );
      }
      if (do_.predecessorRefs() != null) {
        for (var pred : do_.predecessorRefs()) {
          if (!doRefs.contains(pred)) {
            errors.add(
              "dataObject '" + do_.localRef() + "' predecessorRef '" + pred +
              "' not in manifest"
            );
          }
        }
      }
    }

    // 5. Container type validation
    Set<String> containerRefs = manifest.containers() == null
      ? Set.of()
      : manifest.containers().stream()
          .map(ManifestContainerIO::localRef)
          .collect(Collectors.toSet());
    if (manifest.containers() != null) {
      for (var c : manifest.containers()) {
        if (!VALID_CONTAINER_TYPES.contains(c.type())) {
          errors.add(
            "Invalid container type '" + c.type() + "' for localRef '" + c.localRef() + "'"
          );
        }
      }
    }

    // 6. References resolve
    if (manifest.references() != null) {
      for (var ref : manifest.references()) {
        if (!doRefs.contains(ref.dataObjectRef())) {
          errors.add("Reference dataObjectRef '" + ref.dataObjectRef() + "' not in manifest");
        }
        if (!containerRefs.contains(ref.containerRef())) {
          errors.add("Reference containerRef '" + ref.containerRef() + "' not in manifest");
        }
      }
    }

    // 7. Name-conflict warnings (only if collection was found)
    int skipCount = 0;
    if (collOgmId.isPresent()) {
      List<String> candidateNames = manifest.dataObjects().stream()
        .map(do_ -> do_.name())
        .collect(Collectors.toList());
      List<String> existingNames = importPlanDAO.findExistingNames(
        manifest.collectionAppId(), candidateNames);
      for (String name : existingNames) {
        warnings.add("DataObject name already exists in collection: '" + name +
          "' — will be skipped during import");
        skipCount++;
      }
    }

    // Build summary
    int createDo = manifest.dataObjects().size() - skipCount;
    int createContainers = manifest.containers() == null ? 0 : manifest.containers().size();
    int createRefs = manifest.references() == null ? 0 : manifest.references().size();
    ImportSummaryIO summary = new ImportSummaryIO(createDo, createContainers, createRefs, skipCount);

    // On hard errors: return unsaved invalidated plan (no commitId persisted)
    if (!errors.isEmpty()) {
      ImportPlan invalid = new ImportPlan();
      invalid.setStatus("INVALIDATED");
      invalid.setCollectionAppId(manifest.collectionAppId());
      invalid.setValidatedBy(username);
      invalid.setSummaryJson(toJson(summary));
      invalid.setWarningsJson(toJson(warnings));
      // Embed errors in warningsJson won't work cleanly — store nothing extra;
      // the REST resource reads errors from the returned plan's status + its own call context.
      // Errors are passed back via the ImportPlanIO wrapper that the resource assembles.
      // We stash them in a transient field via a known sentinel to keep plan clean.
      // Simplest: store errors concatenated in a temporary property the service caller reads.
      invalid.setManifestHash("ERRORS:" + String.join(";", errors));
      return invalid;
    }

    // 8. Collection fingerprint
    long now = System.currentTimeMillis();
    String rawFingerprint = importPlanDAO.getRawCollectionFingerprintInput(
      manifest.collectionAppId());
    String fingerprint = sha256hex(rawFingerprint);

    // 9. Manifest hash
    String manifestJson = toJson(manifest);
    String manifestHash = sha256hex(manifestJson);

    // 10. commitId = sha256(manifestJson + "|" + fingerprint + "|" + username + "|" + validatedAt)
    String commitInput = manifestJson + "|" + fingerprint + "|" + username + "|" + now;
    String commitId = "sha256:" + sha256hex(commitInput);

    // 11. Check for existing identical plan (same commitId)
    ImportPlan existing = importPlanDAO.findByCommitId(commitId);
    if (existing != null) {
      Log.debugf("IMP1: reusing existing plan %s", commitId);
      return existing;
    }

    // 12. Persist new plan
    ImportPlan plan = new ImportPlan();
    plan.setCommitId(commitId);
    plan.setManifestHash(manifestHash);
    plan.setCollectionFingerprint(fingerprint);
    plan.setCollectionAppId(manifest.collectionAppId());
    plan.setValidatedBy(username);
    plan.setValidatedAt(now);
    plan.setExpiresAt(now + PLAN_TTL_MS);
    plan.setStatus("VALID");
    plan.setSummaryJson(toJson(summary));
    plan.setWarningsJson(toJson(warnings));

    return importPlanDAO.createOrUpdate(plan);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  /**
   * Extract hard errors from an invalidated plan.
   * The service encodes errors in the {@code manifestHash} field using a
   * {@code "ERRORS:<msg>;<msg>"} sentinel when the plan has hard errors.
   *
   * @param plan an (unsaved) plan returned by {@link #validate}
   * @return extracted error strings, or empty list if none
   */
  public List<String> extractErrors(ImportPlan plan) {
    if (plan.getManifestHash() != null && plan.getManifestHash().startsWith("ERRORS:")) {
      String encoded = plan.getManifestHash().substring("ERRORS:".length());
      if (encoded.isEmpty()) return List.of();
      return List.of(encoded.split(";"));
    }
    return List.of();
  }

  /**
   * Extract warnings from a plan's {@code warningsJson} field.
   *
   * @param plan the plan
   * @return list of warning strings, or empty list on parse failure
   */
  public List<String> extractWarnings(ImportPlan plan) {
    return fromJsonList(plan.getWarningsJson());
  }

  /**
   * Extract the summary from a plan's {@code summaryJson} field.
   *
   * @param plan the plan
   * @return the summary, or a zero summary on parse failure
   */
  public ImportSummaryIO extractSummary(ImportPlan plan) {
    if (plan.getSummaryJson() == null) {
      return new ImportSummaryIO(0, 0, 0, 0);
    }
    try {
      return objectMapper.readValue(plan.getSummaryJson(), ImportSummaryIO.class);
    } catch (JsonProcessingException e) {
      Log.warnf("IMP1: failed to deserialise summaryJson for plan %s: %s",
        plan.getAppId(), e.getMessage());
      return new ImportSummaryIO(0, 0, 0, 0);
    }
  }

  // ─── Private helpers ──────────────────────────────────────────────────────

  private String sha256hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandated by the JVM spec — this cannot happen.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      Log.warnf("IMP1: failed to serialise object to JSON: %s", e.getMessage());
      return "{}";
    }
  }

  private List<String> fromJsonList(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      Log.warnf("IMP1: failed to deserialise JSON list: %s", e.getMessage());
      return List.of();
    }
  }

  /** Format epoch millis as ISO-8601 UTC string. */
  public static String toIso8601(long epochMillis) {
    return DateTimeFormatter.ISO_INSTANT.format(
      Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC)
    );
  }
}
