package de.dlr.shepard.spi.transform;

import java.util.Map;

/**
 * V2CONV-B3 — the result of a {@code MAPPING_RECIPE} materialization.
 *
 * <p>A {@link TransformExecutor} derives one of two output shapes from the
 * bound input references:
 *
 * <ul>
 *   <li>A <strong>derived reference</strong> — a brand-new reference (e.g. the
 *       KRL executor derives a {@code TimeseriesReference} of the joint
 *       trajectory). {@link #derivedReferenceAppId()} carries the new appId;
 *       {@link #kind()} is {@code REFERENCE}.</li>
 *   <li>A <strong>view-model</strong> — a played/rendered projection that is
 *       not persisted as a new entity (e.g. a scene-graph play envelope).
 *       {@link #viewModel()} carries the JSON-serialisable map;
 *       {@link #kind()} is {@code VIEW}.</li>
 * </ul>
 *
 * <p>The built-in {@link NoOpTransformExecutor} returns a {@code REFERENCE}
 * result echoing the single input reference appId (an identity transform) so
 * the materialize path is exercisable end-to-end without any plugin — per the
 * CLAUDE.md "ship a working local default for every capability" rule.
 *
 * @param kind                  REFERENCE | VIEW — discriminates which of the
 *                              two payload fields is meaningful; never null
 * @param derivedReferenceAppId appId of the derived reference (when
 *                              {@code kind == REFERENCE}); null for VIEW
 * @param viewModel             JSON-serialisable view-model map (when
 *                              {@code kind == VIEW}); null for REFERENCE
 * @param executorName          the executor that produced this result —
 *                              surfaced on the wire for diagnosability
 */
public record TransformResult(
  Kind kind,
  String derivedReferenceAppId,
  Map<String, Object> viewModel,
  String executorName
) {
  /** Output discriminator for a materialization. */
  public enum Kind {
    /** A new persisted reference; {@link TransformResult#derivedReferenceAppId()} is set. */
    REFERENCE,
    /** An ephemeral played/rendered projection; {@link TransformResult#viewModel()} is set. */
    VIEW,
  }

  public TransformResult {
    if (kind == null) {
      throw new IllegalArgumentException("TransformResult.kind must not be null");
    }
    if (viewModel != null) {
      viewModel = Map.copyOf(viewModel);
    }
  }

  /** Factory: a derived-reference result. */
  public static TransformResult reference(String derivedReferenceAppId, String executorName) {
    return new TransformResult(Kind.REFERENCE, derivedReferenceAppId, null, executorName);
  }

  /** Factory: a view-model result. */
  public static TransformResult view(Map<String, Object> viewModel, String executorName) {
    return new TransformResult(Kind.VIEW, null, viewModel, executorName);
  }
}
