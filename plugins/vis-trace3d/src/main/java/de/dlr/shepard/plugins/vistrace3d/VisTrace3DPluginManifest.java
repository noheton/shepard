package de.dlr.shepard.plugins.vistrace3d;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * VIS-T1 phase 1 — Trace3D VIEW_RECIPE plugin manifest.
 *
 * <p>Phase 1 scope (this commit): manifest-only declaration of the
 * {@code "vis-trace3d"} capability. Surfaces in
 * {@code GET /v2/admin/plugins} with the
 * {@code shepard.plugins.vis-trace3d.enabled} runtime toggle, and
 * carries the {@code Trace3DViewShape} SHACL classpath resource
 * ({@code resources/shapes/trace-3d-view.shacl.ttl}) that the existing
 * {@code POST /v2/shapes/validate} endpoint can load on demand.
 *
 * <p><strong>What this plugin does NOT extract (yet).</strong>
 * <ul>
 *   <li>{@code Trace3DView.vue} / {@code Trace3DCanvas.vue} /
 *       {@code Trace3DChannelPicker.vue} (frontend renderer) — stays
 *       at {@code frontend/components/container/timeseries/} until
 *       the frontend plugin-loading shape lands (currently the
 *       Nuxt build resolves Vue components from the in-tree tree
 *       only).</li>
 *   <li>{@code ShapesRenderRest} ({@code POST /v2/shapes/render})
 *       — stays in core because it dispatches VIEW_RECIPE templates
 *       of any renderer family, not just Trace3D.</li>
 *   <li>{@code TraceFrameResolver} — gated on VIS-S1 (the
 *       {@code ViewRecipeRenderer} SPI dispatcher), which is queued
 *       on {@code aidocs/16}. Without the SPI seam there's nowhere
 *       to plug a resolver in.</li>
 * </ul>
 *
 * <p>Phase 2 (when VIS-S1 lands): move the {@code Trace3DView} Vue
 * components into this module, ship a {@code TraceFrameResolver}
 * implementing the {@code ViewRecipeRenderer} SPI, and gate
 * {@code shapes/render} renderer-hint dispatch via
 * {@code PluginRegistry.isEnabled("vis-trace3d")}.
 *
 * <p>Cross-references:
 * <ul>
 *   <li>{@code aidocs/16-dispatcher-backlog.md} — VIS-T1 row
 *       (queued, sprint 1 of 2)</li>
 *   <li>{@code aidocs/44-fork-vs-upstream-feature-matrix.md} —
 *       VIS-T1 tracker row</li>
 *   <li>{@code aidocs/agent-findings/trace3d-spike.md} — view-shape
 *       input contract (§2 TTL, §3 frame envelope)</li>
 *   <li>{@code aidocs/semantics/98-shapes-views-and-process-model.md} —
 *       VIEW_RECIPE as ShepardTemplate</li>
 *   <li>{@code backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl}
 *       — the upstream meta-shape this plugin's
 *       {@code Trace3DViewShape} extends</li>
 * </ul>
 */
public final class VisTrace3DPluginManifest implements PluginManifest {

  private static final String ID = "vis-trace3d";
  private static final String VERSION = "1.0.0-SNAPSHOT";
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";
  private static final String TITLE = "Trace3D (color-mapped 3D path / brush trace)";
  private static final String DESCRIPTION =
    "Color-mapped 3D brush trace renderer for time-varying end-effector paths " +
    "(robot TCPs, AFP heads, satellite ground tracks). Consumes a VIEW_RECIPE " +
    "ShepardTemplate with X/Y/Z channel bindings plus an optional scalar value " +
    "channel for the inferno/viridis/etc. colour map. Phase 1: capability " +
    "declaration + Trace3DViewShape SHACL resource. The Three.js renderer " +
    "(Trace3DView.vue) and the POST /v2/shapes/render dispatcher remain in-tree " +
    "until the ViewRecipeRenderer SPI (VIS-S1) lands.";
  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");
  private static final String LICENCE = "Apache-2.0";

  /**
   * Classpath path of the Trace3DViewShape SHACL definition. Plugin
   * consumers (the {@code POST /v2/shapes/validate} endpoint and any
   * future SHACL-allow-list reader) can resolve this with
   * {@code VisTrace3DPluginManifest.class.getResource(SHAPE_RESOURCE)}.
   */
  public static final String SHAPE_RESOURCE = "/shapes/trace-3d-view.shacl.ttl";

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
      "VIS-T1: vis-trace3d plugin v%s active via PluginManifest SPI (id=%s, compat=%s, shape=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY,
      SHAPE_RESOURCE
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("VIS-T1: vis-trace3d plugin onUnregister invoked");
  }
}
