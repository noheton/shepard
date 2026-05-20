package de.dlr.shepard.plugins.video;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;

/**
 * VID1b phase 1 — Video payload kind plugin manifest.
 *
 * <p>Phase 1: manifest-only. The VideoStreamReference entity,
 * VideoStreamReferenceV2Rest, VideoAnnotationRest, and VideoProbeService
 * remain in-tree for the migration window. This manifest exists so that:
 * <ul>
 *   <li>{@code PluginRegistry} tracks {@code "video"} in
 *       {@code GET /v2/admin/plugins} with the {@code shepard.plugins.video.enabled}
 *       runtime toggle.</li>
 *   <li>{@code shepard-plugin-imagebundle} can declare
 *       {@code new PluginDependency("video", ">=6.0.0-SNAPSHOT")} and have
 *       it resolve on startup.</li>
 * </ul>
 *
 * <p>Phase 2 (VID1b full): move all
 * {@code de.dlr.shepard.context.references.videostreamreference.*} and
 * {@code de.dlr.shepard.v2.video.*} packages into this module and gate
 * the REST resources behind {@code PluginRegistry.isEnabled("video")}.
 */
public final class VideoPluginManifest implements PluginManifest {

  private static final String ID = "video";
  private static final String VERSION = "1.0.0-SNAPSHOT";
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

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
    return "Video (VideoStreamReference)";
  }

  @Override
  public String description() {
    return "Video payload kind. Phase 1: capability declaration for PluginRegistry. " +
      "VideoStreamReference entity + REST endpoints are in-tree pending VID1b full extraction.";
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "VID1b: video plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("VID1b: video plugin onUnregister invoked");
  }
}
