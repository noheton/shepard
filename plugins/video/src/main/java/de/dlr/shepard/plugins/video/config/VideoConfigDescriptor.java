package de.dlr.shepard.plugins.video.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.plugins.video.io.VideoConfigIO;
import de.dlr.shepard.plugins.video.services.VideoConfigService;
import de.dlr.shepard.plugins.video.services.VideoConfigService.VideoPatch;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * V2CONV-A7 — {@link ConfigDescriptor} for the video plugin runtime config,
 * exposed as {@code GET|PATCH /v2/admin/config/video}. Replaces the bespoke
 * {@code GET|PATCH /v2/admin/video/config} methods that were deleted from
 * the old {@code VideoAdminRest}.
 *
 * <p>Patchable fields: {@code ffprobeEnabled} (Boolean, absent = leave alone,
 * explicit-null = leave alone), {@code maxFileSizeMb} (Long or explicit null
 * to clear the upload cap).
 */
@ApplicationScoped
public class VideoConfigDescriptor implements ConfigDescriptor<VideoConfigIO> {

  @Inject
  VideoConfigService service;

  @Override
  public String featureName() {
    return "video";
  }

  @Override
  public String description() {
    return "Video plugin: ffprobe probe toggle and per-upload file-size cap.";
  }

  @Override
  public VideoConfigIO currentShape() {
    return VideoConfigIO.from(service.current());
  }

  @Override
  public VideoConfigIO applyMergePatch(JsonNode patch) {
    VideoPatch svcPatch = new VideoPatch();

    if (patch.has("ffprobeEnabled")) {
      JsonNode node = patch.get("ffprobeEnabled");
      if (node != null && !node.isNull()) {
        svcPatch.ffprobeEnabled = node.asBoolean();
      }
      // explicit null on a boolean toggle — leave alone
    }

    if (patch.has("maxFileSizeMb")) {
      svcPatch.maxFileSizeMbTouched = true;
      JsonNode node = patch.get("maxFileSizeMb");
      svcPatch.maxFileSizeMb = (node == null || node.isNull()) ? null : node.longValue();
    }

    return VideoConfigIO.from(service.patch(svcPatch));
  }
}
