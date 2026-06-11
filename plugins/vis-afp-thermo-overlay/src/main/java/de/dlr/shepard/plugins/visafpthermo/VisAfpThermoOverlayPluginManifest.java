package de.dlr.shepard.plugins.visafpthermo;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * MFFD-RENDER-AFP-THERMO-OVERLAY slice 1 — {@code "vis-afp-thermo-overlay"} plugin manifest.
 *
 * <p>Slice 1 scope (this module): manifest-only declaration of the
 * {@code "vis-afp-thermo-overlay"} capability. Surfaces in
 * {@code GET /v2/admin/plugins} with the
 * {@code shepard.plugins.vis-afp-thermo-overlay.enabled} runtime toggle, and
 * carries the {@code AfpThermoOverlayShape} SHACL classpath resource
 * ({@link #SHAPE_RESOURCE}) that {@code POST /v2/shapes/validate} loads.
 *
 * <p><strong>What this slice ships:</strong>
 * <ul>
 *   <li>The {@link #SHAPE_IRI} dispatch key constant (public API for the
 *       materialize endpoint and the docs/reference page).</li>
 *   <li>{@link #SHAPE_RESOURCE} — SHACL TTL at classpath path
 *       {@code /shapes/afp-thermo-overlay.shacl.ttl}, defining the
 *       required + optional bindings for the dual-pane AFP+NDT overlay.</li>
 *   <li>Lifecycle hooks (onRegister logs the active state; onUnregister
 *       is a no-op in slice 1 — no executor or caches to clean up).</li>
 * </ul>
 *
 * <p><strong>What this slice does NOT ship (yet):</strong>
 * <ul>
 *   <li>Slice 2 — {@code AfpThermoOverlayTransformExecutor}: the
 *       {@link de.dlr.shepard.spi.transform.TransformExecutor} that
 *       resolves AFP TCP timeseries + OTvis NDT data for the same tile
 *       and returns the dual-pane VIEW envelope (Trace3D trajectory frames +
 *       NDT heatmap pixel buffer).</li>
 *   <li>Slice 3 — {@code AfpThermoOverlayCanvas.vue}: the full Vue synced
 *       dual-pane renderer with Vitest coverage + Playwright at 4K viewport.</li>
 * </ul>
 *
 * <p>The frontend placeholder stub is at
 * {@code frontend/components/mffd/AfpThermoOverlayPlaceholder.vue}.
 *
 * <p>Cross-references:
 * <ul>
 *   <li>{@code aidocs/16-dispatcher-backlog.md} — MFFD-RENDER-AFP-THERMO-OVERLAY row</li>
 *   <li>{@code aidocs/44-fork-vs-upstream-feature-matrix.md} — MFFD tracker row</li>
 *   <li>{@code plugins/vis-afp-thermo-overlay/docs/reference.md} — full shape docs</li>
 *   <li>{@code backend/src/main/java/de/dlr/shepard/v2/shapes/mffd/MffdAfpCourseKind.java}
 *       — the AFP course DataObject template the executor reads from</li>
 *   <li>{@code backend/src/main/java/de/dlr/shepard/v2/shapes/mffd/MffdNdtOtvisMeasurementKind.java}
 *       — the NDT DataObject template the executor reads from</li>
 *   <li>{@code plugins/vis-trace3d/} — Trace3D renderer (slice 2 builds on this)</li>
 * </ul>
 */
public final class VisAfpThermoOverlayPluginManifest implements PluginManifest {

  private static final String ID = "vis-afp-thermo-overlay";
  private static final String VERSION = "1.0.0-SNAPSHOT";
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";
  private static final String TITLE =
      "AFP Thermo Overlay (AFP TCP trace + OTvis NDT synced dual-pane renderer)";
  private static final String DESCRIPTION =
      "Renders a synced dual-pane view of AFP process data and OTvis NDT inspection "
          + "results for the same (Section, Module, Layer) tile. "
          + "Left pane: Trace3D 3D trajectory of the AFP robot head, vertex-colour mapped "
          + "by a selected TCP channel (tcp-temperature / consolidation-force / head-speed / "
          + "nip-pressure). "
          + "Right pane: OTvis thermography heatmap from the post-layup NDT inspection "
          + "of the same tile. "
          + "The canonical MFFD 'process vs. inspection' comparison view — lets an engineer "
          + "see in one glance whether a consolidation-force anomaly at ply 18 of tile S4·M13 "
          + "corresponds to an NDT-flagged delamination zone. "
          + "Slice 1: AfpThermoOverlayShape SHACL classpath resource + capability declaration. "
          + "Slice 2 ships AfpThermoOverlayTransformExecutor; slice 3 ships AfpThermoOverlayCanvas.vue.";
  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");
  private static final String LICENCE = "Apache-2.0";

  /**
   * Classpath path of the AfpThermoOverlayShape SHACL definition. Resolved via
   * {@code VisAfpThermoOverlayPluginManifest.class.getResource(SHAPE_RESOURCE)}.
   */
  public static final String SHAPE_RESOURCE = "/shapes/afp-thermo-overlay.shacl.ttl";

  /** The MAPPING_RECIPE shape IRI that the slice-2 executor will claim. */
  public static final String SHAPE_IRI =
      "http://semantics.dlr.de/shepard/transform#AfpThermoOverlayShape";

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
        "MFFD-RENDER-AFP-THERMO-OVERLAY: vis-afp-thermo-overlay plugin v%s active "
            + "(id=%s, compat=%s, shape=%s). "
            + "Executor (slice 2) and renderer (slice 3) pending.",
        VERSION,
        ID,
        SHEPARD_COMPATIBILITY,
        SHAPE_RESOURCE);
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("MFFD-RENDER-AFP-THERMO-OVERLAY: vis-afp-thermo-overlay plugin onUnregister invoked");
  }
}
