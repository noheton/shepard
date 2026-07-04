package de.dlr.shepard.spi.transform;

import java.util.Set;

/**
 * V2CONV-B3 — the built-in local default {@link TransformExecutor}, shipped so
 * the {@code POST /v2/mappings/{templateAppId}/materialize} path is exercisable
 * end-to-end <em>without any plugin</em> (CLAUDE.md "ship a working local
 * default for every capability").
 *
 * <p>It claims the canonical identity shape
 * {@value #IDENTITY_SHAPE_IRI} and performs an <strong>identity
 * transform</strong>: it echoes the first bound input reference appId back as
 * the derived-reference result. This is a real (if trivial) materialization —
 * not a stub that returns 500 — so an operator can author a {@code
 * MAPPING_RECIPE} targeting this shape and see the path work before any
 * scene-graph / KRL plugin is installed.
 *
 * <p>Registered via
 * {@code META-INF/services/de.dlr.shepard.spi.transform.TransformExecutor}, the
 * same ServiceLoader contract every {@link TransformExecutor} uses.
 */
public class NoOpTransformExecutor implements TransformExecutor {

  /** The canonical identity-transform shape IRI this default claims. */
  public static final String IDENTITY_SHAPE_IRI =
    "http://semantics.dlr.de/shepard/transform#IdentityTransformShape";

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(IDENTITY_SHAPE_IRI);
  }

  @Override
  public TransformResult materialize(TransformRequest req) {
    if (req == null) {
      throw new TransformException("transform.body.invalid", "request must not be null");
    }
    var inputs = req.inputReferenceAppIdValues();
    if (inputs.isEmpty()) {
      throw new TransformException(
        "transform.input.missing",
        "identity transform requires at least one input reference appId"
      );
    }
    // Identity: the derived reference IS the first input reference. No new
    // entity is minted — the derived output simply points at the existing
    // reference, the cheapest materialization that still exercises the path.
    return TransformResult.reference(inputs.get(0), name());
  }

  @Override
  public String name() {
    return "NoOpTransformExecutor";
  }
}
