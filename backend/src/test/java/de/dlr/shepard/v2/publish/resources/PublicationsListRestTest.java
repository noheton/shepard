package de.dlr.shepard.v2.publish.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-PUBLICATIONS-KIND-410 unit tests for the tombstoned {@link PublicationsListRest}.
 *
 * <p>All calls must return 410 Gone with a {@code Location} header pointing to
 * {@code /v2/publications} and a problem+json body.
 */
class PublicationsListRestTest {

  private PublicationsListRest rest;

  @BeforeEach
  void setUp() {
    rest = new PublicationsListRest();
  }

  @Test
  void anyCallReturns410Gone() {
    Response r = rest.list("data-objects", "01HF-A");
    assertEquals(410, r.getStatus());
  }

  @Test
  void responseHasLocationHeader() {
    Response r = rest.list("data-objects", "01HF-A");
    assertEquals("/v2/publications", r.getHeaderString("Location"));
  }

  @Test
  void responseIsApplicationProblemJson() {
    Response r = rest.list("data-objects", "01HF-A");
    assertNotNull(r.getMediaType());
    assertTrue(r.getMediaType().toString().contains("problem+json"),
      "Response media type should be application/problem+json");
  }

  @Test
  void differentKindAndAppIdStillReturns410() {
    Response r = rest.list("collections", "01COLL-A");
    assertEquals(410, r.getStatus());
    assertEquals("/v2/publications", r.getHeaderString("Location"));
  }

  @Test
  void problemBodyContainsGoneType() {
    Response r = rest.list("data-objects", "01HF-A");
    String body = r.getEntity().toString();
    assertTrue(body.contains("urn:shepard:error:gone"), "Problem body should contain gone type");
  }
}
