package de.dlr.shepard.plugins.visndtgrid;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * MFFD-RENDER-NDT-GRID slice 1 — {@code "vis-ndt-grid"} plugin manifest.
 *
 * <p>Slice 1 scope (this module): manifest-only declaration of the
 * {@code "vis-ndt-grid"} capability. Surfaces in
 * {@code GET /v2/admin/plugins} with the
 * {@code shepard.plugins.vis-ndt-grid.enabled} runtime toggle, and
 * carries the {@code NdtGridShape} SHACL classpath resource
 * ({@link #SHAPE_RESOURCE}) that {@code POST /v2/shapes/validate} loads.
 *
 * <p><strong>What this slice does NOT ship (yet):</strong>
 * <ul>
 *   <li>Slice 2 — {@code NdtGridTransformExecutor}: the
 *       {@link de.dlr.shepard.spi.transform.TransformExecutor} that
 *       resolves NDT OTvis DataObjects in the bound Collection, computes
 *       the S×M×L×F tile grid, and returns the VIEW envelope.</li>
 *   <li>Slice 3 — {@code NdtGridView.vue}: the Vue component that
 *       renders the grid envelope as a colour-mapped 2D mosaic with
 *       Vitest coverage.</li>
 * </ul>
 *
 * <p>The frontend placeholder stub is at
 * {@code frontend/components/mffd/NdtGridViewPlaceholder.vue}.
 *
 * <p>Cross-references:
 * <ul>
 *   <li>{@code aidocs/16-dispatcher-backlog.md} — MFFD-RENDER-NDT-GRID row</li>
 *   <li>{@code aidocs/44-fork-vs-upstream-feature-matrix.md} — MFFD tracker row</li>
 *   <li>{@code plugins/vis-ndt-grid/docs/reference.md} — full endpoint + shape docs</li>
 *   <li>{@code backend/src/main/java/de/dlr/shepard/v2/shapes/mffd/MffdNdtOtvisMeasurementKind.java}
 *       — the DATAOBJECT_RECIPE template the executor queries against</li>
 * </ul>
 */
public final class VisNdtGridPluginManifest implements PluginManifest {

  private static final String ID = "vis-ndt-grid";
  private static final String VERSION = "1.0.0-SNAPSHOT";
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";
  private static final String TITLE = "NDT Grid (thermography tile mosaic renderer)";
  private static final String DESCRIPTION =
    "Renders an NDT OTvis thermography Collection as a 2D S×M×L×F tile-grid mosaic. " +
    "Cell colour encodes mean ΔT (hot/plasma/gray colour maps, quantitative thermal view) " +
    "or pass/fail/review quality disposition (AS9100 §8.7 traceability view). " +
    "Canonical use case: 'Show me layer 18 of the MFFD Q1 upper-fuselage AFP track'. " +
    "Slice 1: NdtGridShape SHACL classpath resource + capability declaration. " +
    "Slice 2 ships NdtGridTransformExecutor; slice 3 ships NdtGridView.vue.";
  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");
  private static final String LICENCE = "Apache-2.0";

  /**
   * Classpath path of the NdtGridShape SHACL definition. Plugin consumers
   * (the {@code POST /v2/shapes/validate} endpoint and the future allow-list
   * reader) resolve this via
   * {@code VisNdtGridPluginManifest.class.getResource(SHAPE_RESOURCE)}.
   */
  public static final String SHAPE_RESOURCE = "/shapes/ndt-grid.shacl.ttl";

  /** The MAPPING_RECIPE shape IRI that the slice-2 executor will claim. */
  public static final String SHAPE_IRI =
    "http://semantics.dlr.de/shepard/transform#NdtGridShape";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String version() {
    return VERSION;
  }

  @Override
  public String shepardCompatibility() {
    return SHEPARD_COMPATIBILITY;
  }

  @Override
  public String title() {
    return TITLE;
  }

  @Override
  public String description() {
    return DESCRIPTION;
  }

  @Override
  public Optional<URI> repositoryUrl() {
    return Optional.of(REPOSITORY);
  }

  @Override
  public String licence() {
    return LICENCE;
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "MFFD-RENDER-NDT-GRID: vis-ndt-grid plugin v%s active (id=%s, compat=%s, shape=%s). " +
      "Executor (slice 2) active. Renderer (slice 3) pending.",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY,
      SHAPE_RESOURCE
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("MFFD-RENDER-NDT-GRID: vis-ndt-grid plugin onUnregister invoked");
  }
}
