package de.dlr.shepard.v2.video.resources;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-VIDEO-STREAMREF-PATH tombstone tests — verifies that the old
 * per-kind video endpoints return 410 Gone with a problem+json body that
 * directs callers to the new paths.
 *
 * <p>Range-aware download and permission logic are now exercised through the
 * unified {@code GET /v2/references/{appId}/content} endpoint tested at the
 * Quarkus IT layer.
 */
class VideoStreamReferenceV2RestTest {

  static final String DO_APP_ID = "do-app-id-abc";
  static final String REF_APP_ID = "ref-app-id-xyz";

  VideoStreamReferenceV2Rest resource;

  @BeforeEach
  void setUp() {
    resource = new VideoStreamReferenceV2Rest();
  }

  @Test
  void upload_returns410Gone() {
    Response r = resource.upload(DO_APP_ID, "video.mp4", null, null);
    assertThat(r.getStatus()).isEqualTo(410);
    assertThat(r.getMediaType().toString()).contains("application/problem+json");
  }

  @Test
  void download_returns410Gone() {
    Response r = resource.download(DO_APP_ID, REF_APP_ID, null, null);
    assertThat(r.getStatus()).isEqualTo(410);
    assertThat(r.getMediaType().toString()).contains("application/problem+json");
  }

  @Test
  void download_410BodyMentionsNewPath() {
    Response r = resource.download(DO_APP_ID, REF_APP_ID, "bytes=0-100", null);
    assertThat(r.getStatus()).isEqualTo(410);
    Object entity = r.getEntity();
    assertThat(entity).isNotNull();
    assertThat(entity.toString()).contains("/v2/references");
  }
}
