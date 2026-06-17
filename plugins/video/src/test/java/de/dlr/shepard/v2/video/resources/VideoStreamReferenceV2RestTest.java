package de.dlr.shepard.v2.video.resources;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-VIDEO-STREAMREF-PATH — plain unit tests for the 410 tombstone
 * {@link VideoStreamReferenceV2Rest}.
 *
 * <p>Both retired paths must return 410 Gone with an {@code application/problem+json}
 * body and a {@code Link} header pointing to the successor endpoint.
 */
class VideoStreamReferenceV2RestTest {

  static final String DO_APP_ID = "do-app-id-abc";
  static final String REF_APP_ID = "ref-app-id-xyz";

  VideoStreamReferenceV2Rest resource;

  @BeforeEach
  void setUp() {
    resource = new VideoStreamReferenceV2Rest();
  }

  // ── upload tombstone ──────────────────────────────────────────────────────

  @Test
  void upload_returns410Gone() {
    Response r = resource.upload(DO_APP_ID, null, null);
    assertThat(r.getStatus()).isEqualTo(410);
  }

  @Test
  void upload_hasSuccessorLinkHeader() {
    Response r = resource.upload(DO_APP_ID, "my-video", null);
    String link = r.getHeaderString("Link");
    assertThat(link).contains("/v2/references").contains("successor-version");
  }

  @Test
  void upload_hasApplicationProblemJsonContentType() {
    Response r = resource.upload(DO_APP_ID, null, null);
    assertThat(r.getMediaType().toString()).contains("application/problem+json");
  }

  // ── download tombstone ────────────────────────────────────────────────────

  @Test
  void download_returns410Gone() {
    Response r = resource.download(DO_APP_ID, REF_APP_ID, null);
    assertThat(r.getStatus()).isEqualTo(410);
  }

  @Test
  void download_hasSuccessorLinkHeaderWithRefAppId() {
    Response r = resource.download(DO_APP_ID, REF_APP_ID, "bytes=0-100");
    String link = r.getHeaderString("Link");
    assertThat(link)
      .contains(REF_APP_ID)
      .contains("successor-version")
      .contains("/v2/references/" + REF_APP_ID + "/download");
  }

  @Test
  void download_hasApplicationProblemJsonContentType() {
    Response r = resource.download(DO_APP_ID, REF_APP_ID, null);
    assertThat(r.getMediaType().toString()).contains("application/problem+json");
  }
}
