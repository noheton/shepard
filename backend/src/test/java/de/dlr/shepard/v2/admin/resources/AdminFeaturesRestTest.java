package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// APISIMP-FEATURE-TOGGLE-CONFIG-UNIFY — AdminFeaturesRest is tombstoned;
// tests verify 410 Gone behaviour and that the role gate is still in place.

class AdminFeaturesRestTest {

  AdminFeaturesRest resource;

  @BeforeEach
  void setUp() {
    resource = new AdminFeaturesRest();
  }

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = AdminFeaturesRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "AdminFeaturesRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals("instance-admin", gate.value()[0]);
  }

  @Test
  void listReturns410Gone() {
    var r = resource.list();
    assertEquals(410, r.getStatus());
  }

  @Test
  void listResponseBodyIsProblemJson() {
    var r = resource.list();
    ProblemJson body = (ProblemJson) r.getEntity();
    assertNotNull(body);
    assertTrue(
      body.detail().contains("/v2/admin/config/feature-toggles"),
      "410 body detail must reference the successor path"
    );
    assertEquals("urn:shepard:error:gone", body.type());
  }

  @Test
  void listResponseCarriesLocationAndLinkHeaders() {
    var r = resource.list();
    String location = (String) r.getHeaders().getFirst("Location");
    assertNotNull(location, "410 response must carry a Location header");
    assertEquals("/v2/admin/config/feature-toggles", location);
    String link = (String) r.getHeaders().getFirst("Link");
    assertNotNull(link, "410 response must carry a Link header");
    assertTrue(
      link.contains("/v2/admin/config/feature-toggles"),
      "Link header must point to the successor resource"
    );
  }

  @Test
  void patchReturns410Gone() {
    var r = resource.patch("versioning");
    assertEquals(410, r.getStatus());
  }

  @Test
  void patchResponseBodyIsProblemJson() {
    var r = resource.patch("any-toggle");
    ProblemJson body = (ProblemJson) r.getEntity();
    assertNotNull(body);
    assertTrue(
      body.detail().contains("/v2/admin/config/feature-toggles"),
      "410 body detail must reference the successor path"
    );
  }
}
