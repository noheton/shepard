package de.dlr.shepard.data.timeseries.services;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.services.ChannelUnitInferenceService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TS-SEMANTIC-01 — dual-write service that mirrors the 5-tuple channel identity
 * from {@code channel_metadata} (Postgres) into {@link SemanticAnnotation} nodes
 * (Neo4j), linked via {@link AnnotatableTimeseries}.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Called from {@link de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository#upsert}
 *       immediately after a channel row is successfully created ({@code cmRows > 0}).</li>
 *   <li>Only non-null, non-blank field values are written as annotation nodes.</li>
 *   <li>Idempotent: if an {@link AnnotatableTimeseries} node already exists for the
 *       given {@code (containerId, timeseriesId)}, it is reused; existing annotations
 *       of source {@value Constants#ANNOTATION_SOURCE_TS_CHANNEL_METADATA} are deleted
 *       first to avoid duplicates on the rare code path where upsert is called twice
 *       for the same channel.</li>
 *   <li>Best-effort: Neo4j failures are caught and logged at WARN level; the Postgres
 *       write is never rolled back as a consequence.
 *       TODO(TS-SEMANTIC-02): replace with async queue to de-risk hot-path latency for
 *       bulk channel creation (e.g. MFFD ingest with thousands of channels).</li>
 * </ul>
 *
 * <h2>Predicate IRIs (all defined in {@link Constants})</h2>
 * <ul>
 *   <li>{@code measurement} → {@value Constants#TS_PREDICATE_MEASUREMENT}</li>
 *   <li>{@code field}       → {@value Constants#TS_PREDICATE_FIELD}</li>
 *   <li>{@code device}      → {@value Constants#TS_PREDICATE_DEVICE}</li>
 *   <li>{@code location}    → {@value Constants#TS_PREDICATE_LOCATION}</li>
 *   <li>{@code symbolicName}→ {@value Constants#TS_PREDICATE_SYMBOLIC_NAME}</li>
 * </ul>
 *
 * <h2>SEMA-V6 fields</h2>
 * <ul>
 *   <li>{@code sourceMode}  = {@code "ai"} — per TS-SEMANTIC-01 spec.  Carries a
 *       technical debt marker: channel metadata is system-generated, not AI-inferred;
 *       future TS-SEMANTIC-02 should introduce a {@code "system"} sourceMode value.</li>
 *   <li>{@code subjectKind} = {@value Constants#SUBJECT_KIND_ANNOTATABLE_TIMESERIES}</li>
 *   <li>{@code subjectAppId}= the channel's {@code shepardId} (UUID) so that
 *       {@link AnnotatableTimeseries#getAppId()} == {@code TimeseriesEntity.shepardId.toString()}.
 *       This bridges Postgres ↔ Neo4j with one shared UUID for Phase 3 read-flip.</li>
 *   <li>{@code source}      = {@value Constants#ANNOTATION_SOURCE_TS_CHANNEL_METADATA}</li>
 * </ul>
 *
 * @see de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository
 * @see de.dlr.shepard.context.semantic.services.AttributeAnnotationDualWriteService
 */
@ApplicationScoped
public class TimeseriesSemanticDualWriteService {

  @Inject
  AnnotatableTimeseriesDAO annotatableTimeseriesDAO;

  @Inject
  ChannelUnitInferenceService channelUnitInferenceService;

  /**
   * Dual-write the 5-tuple channel identity as {@link SemanticAnnotation} nodes attached
   * to an {@link AnnotatableTimeseries} subject node.
   *
   * <p>Callers must only invoke this after a successful {@code channel_metadata} INSERT
   * ({@code cmRows > 0}).  The method is safe to call from within a JPA transaction
   * because Neo4j OGM sessions are not JTA-enrolled; Neo4j writes commit immediately.
   *
   * @param containerId  the owning container's DB id
   * @param timeseriesId the newly-created {@code timeseries.id} row id
   * @param shepardId    the channel's stable UUID (from {@code timeseries.shepard_id})
   * @param measurement  channel measurement identity field; may be null
   * @param field        channel field identity field; may be null
   * @param device       channel device identity field; may be null
   * @param location     channel location identity field; may be null
   * @param symbolicName channel symbolic name identity field; may be null
   */
  public void dualWriteChannelMetadata(
    long containerId,
    int timeseriesId,
    String shepardId,
    String measurement,
    String field,
    String device,
    String location,
    String symbolicName
  ) {
    if (shepardId == null || shepardId.isBlank()) {
      Log.warnf(
        "TS-SEMANTIC-01 dual-write: shepardId is null/blank for container=%d tsId=%d — skipping",
        containerId, timeseriesId
      );
      return;
    }

    try {
      // Build predicate → value map for only non-blank fields
      Map<String, String> fieldPredicates = new LinkedHashMap<>();
      if (isNonBlank(measurement)) fieldPredicates.put(Constants.TS_PREDICATE_MEASUREMENT, measurement);
      if (isNonBlank(field))       fieldPredicates.put(Constants.TS_PREDICATE_FIELD,        field);
      if (isNonBlank(device))      fieldPredicates.put(Constants.TS_PREDICATE_DEVICE,       device);
      if (isNonBlank(location))    fieldPredicates.put(Constants.TS_PREDICATE_LOCATION,     location);
      if (isNonBlank(symbolicName)) fieldPredicates.put(Constants.TS_PREDICATE_SYMBOLIC_NAME, symbolicName);

      if (fieldPredicates.isEmpty()) {
        Log.debugf(
          "TS-SEMANTIC-01 dual-write: all 5-tuple fields blank for container=%d tsId=%d — no annotations written",
          containerId, timeseriesId
        );
        return;
      }

      // Find or create the AnnotatableTimeseries node
      AnnotatableTimeseries subject = annotatableTimeseriesDAO
        .findByTimeseries(containerId, timeseriesId)
        .orElseGet(() -> {
          AnnotatableTimeseries newNode = new AnnotatableTimeseries(containerId, timeseriesId, new ArrayList<>());
          // Bridge: set appId to the channel's shepardId UUID so Phase 3 can use it as the
          // single stable cross-substrate identifier.
          newNode.setAppId(shepardId);
          return newNode;
        });

      // Purge any stale ts-channel-metadata + ts-channel-unit-suffix annotations
      // to ensure idempotency on re-call (e.g. operator-triggered re-upsert).
      List<SemanticAnnotation> existing = new ArrayList<>(subject.getAnnotations());
      List<SemanticAnnotation> stale = existing.stream()
        .filter(a ->
          Constants.ANNOTATION_SOURCE_TS_CHANNEL_METADATA.equals(a.getSource())
          || Constants.ANNOTATION_SOURCE_TS_CHANNEL_UNIT_SUFFIX.equals(a.getSource())
        )
        .toList();
      for (SemanticAnnotation staleAnn : stale) {
        if (staleAnn.getId() != null) {
          annotatableTimeseriesDAO.deleteAnnotation(staleAnn.getId());
        }
      }
      if (!stale.isEmpty()) {
        // Reload after deletion to get clean state
        subject = annotatableTimeseriesDAO
          .findByTimeseries(containerId, timeseriesId)
          .orElseGet(() -> new AnnotatableTimeseries(containerId, timeseriesId, new ArrayList<>()));
        if (subject.getAppId() == null) {
          subject.setAppId(shepardId);
        }
      }

      // Create one SemanticAnnotation per non-blank field
      for (Map.Entry<String, String> entry : fieldPredicates.entrySet()) {
        String predicateIri = entry.getKey();
        String value = entry.getValue();

        SemanticAnnotation annotation = new SemanticAnnotation();
        annotation.setAppId(AppIdGenerator.next());
        annotation.setPropertyIRI(predicateIri);
        annotation.setPropertyName(localName(predicateIri));
        annotation.setValueName(value);
        annotation.setSource(Constants.ANNOTATION_SOURCE_TS_CHANNEL_METADATA);

        // SEMA-V6 fields
        annotation.setSubjectKind(Constants.SUBJECT_KIND_ANNOTATABLE_TIMESERIES);
        annotation.setSubjectAppId(shepardId);  // bridges to timeseries.shepard_id
        annotation.setVocabularyId(null);        // no controlled vocabulary for raw channel fields
        // TODO(TS-SEMANTIC-02): introduce "system" sourceMode; "ai" is used per TS-SEMANTIC-01 spec
        // as a temporary measure until the enum is extended.
        annotation.setSourceMode("ai");
        annotation.setSourceActivityAppId(null);
        annotation.setValidFromMillis(null);
        annotation.setValidUntilMillis(null);
        annotation.setConfidence(null);

        subject.addAnnotation(annotation);
      }

      // ── AI1v Phase 1 — deterministic unit inference from `field` name ─────
      // We co-locate the unit annotation with the 5-tuple write because (a) we
      // already hold the bridge node and the transaction, and (b) "data
      // complete at write" beats a separate periodic job. The AI1c quality
      // score is intentionally (b)-shaped because it needs data points to
      // score; unit inference is a pure function of the field name and has no
      // such prerequisite. See aidocs/16 row AI1v for the full design
      // argument and persona consult.
      if (isNonBlank(field)) {
        var guess = channelUnitInferenceService.infer(field);
        if (guess.isPresent() && guess.get().isResolved()) {
          var g = guess.get();
          SemanticAnnotation unitAnnotation = new SemanticAnnotation();
          unitAnnotation.setAppId(AppIdGenerator.next());
          unitAnnotation.setPropertyIRI(Constants.TS_UNIT_PREDICATE);
          unitAnnotation.setPropertyName("unit");
          unitAnnotation.setValueIRI(g.unitIri());
          unitAnnotation.setValueName(g.label());
          unitAnnotation.setSource(Constants.ANNOTATION_SOURCE_TS_CHANNEL_UNIT_SUFFIX);
          unitAnnotation.setSubjectKind(Constants.SUBJECT_KIND_ANNOTATABLE_TIMESERIES);
          unitAnnotation.setSubjectAppId(shepardId);
          unitAnnotation.setVocabularyId(null);
          // sourceMode "ai" per AI1v contract — the inference is automated and
          // not yet operator-confirmed.  A human accept-flip via the UI will
          // promote this to "collaborative" (AI1v Phase 3).
          unitAnnotation.setSourceMode("ai");
          unitAnnotation.setSourceActivityAppId(null);
          unitAnnotation.setValidFromMillis(null);
          unitAnnotation.setValidUntilMillis(null);
          // Confidence is tier-derived: SUFFIX is the strongest signal (1.0),
          // PREFIX_HEURISTIC slightly less (0.85), WELDING_CAP domain-specific
          // (0.9). Phase 2 LLM outputs will populate this directly.
          unitAnnotation.setConfidence(switch (g.tier()) {
            case SUFFIX -> 1.0;
            case WELDING_CAP -> 0.9;
            case PREFIX_HEURISTIC -> 0.85;
            case AMBIGUOUS -> null;
          });
          subject.addAnnotation(unitAnnotation);
          Log.debugf(
            "AI1v: container=%d tsId=%d shepardId=%s field=%s → %s (tier=%s)",
            containerId, timeseriesId, shepardId, field, g.unitIri(), g.tier()
          );
        } else if (guess.isPresent()) {
          // AMBIGUOUS — Phase 2 (LLM) will resolve once AI1a is wired.
          Log.warnf(
            "AI1v: container=%d tsId=%d shepardId=%s field=%s → AMBIGUOUS (Phase 1 cannot resolve; "
            + "Phase 2 LLM gate AI1a is pending — annotation skipped)",
            containerId, timeseriesId, shepardId, field
          );
        }
      }

      annotatableTimeseriesDAO.createOrUpdate(subject);

      Log.debugf(
        "TS-SEMANTIC-01 dual-write: container=%d tsId=%d shepardId=%s — wrote %d annotation(s)",
        containerId, timeseriesId, shepardId, fieldPredicates.size()
      );
    } catch (Throwable t) {
      // Best-effort: Neo4j write failure must never propagate to the Postgres write path.
      Log.warnf(
        "TS-SEMANTIC-01 dual-write FAILED (Neo4j) for container=%d tsId=%d shepardId=%s — " +
        "Postgres write unaffected. Cause: %s",
        containerId, timeseriesId, shepardId, t.getMessage()
      );
    }
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private static boolean isNonBlank(String s) {
    return s != null && !s.isBlank();
  }

  /**
   * Extracts the local name from a URN-style predicate IRI for use as {@code propertyName}.
   * E.g. {@code "urn:shepard:channel:measurement"} → {@code "measurement"}.
   */
  static String localName(String iri) {
    if (iri == null) return "";
    int colon = iri.lastIndexOf(':');
    return colon >= 0 ? iri.substring(colon + 1) : iri;
  }
}
