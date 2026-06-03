package de.dlr.shepard.v2.admin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A4 — unit tests for the generic admin config endpoint.
 *
 * No Quarkus boot — {@link ConfigRegistry} is constructed directly;
 * {@link ConfigDescriptor} implementations are mocked. Tests cover:
 * auth gate, path annotation, GET round-trip, PATCH success, PATCH
 * validation failure → 400, unknown feature → 404, null/empty registry.
 */
class AdminConfigRestTest {

  private ConfigRegistry registry;
  private AdminConfigRest rest;

  @BeforeEach
  void setUp() {
    registry = new ConfigRegistry();
    rest = new AdminConfigRest();
    rest.registry = registry;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = AdminConfigRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "AdminConfigRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminConfigFeature() {
    Path p = AdminConfigRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/config/{feature}", p.value(),
      "generic endpoint lives on /v2/ shelf per V2CONV-A4");
  }

  // ─── GET: known feature returns read() payload ────────────────────────────

  @Test
  void getConfig_knownFeature_returns200WithReadPayload() {
    ConfigDescriptor desc = mock(ConfigDescriptor.class);
    when(desc.featureName()).thenReturn("my-feature");
    when(desc.read()).thenReturn("snapshot-payload");
    registry.register(desc);

    Response r = rest.getConfig("my-feature");

    assertEquals(200, r.getStatus());
    assertEquals("snapshot-payload", r.getEntity());
  }

  // ─── GET: unknown feature → 404 ─────────────────────────────────────────

  @Test
  void getConfig_unknownFeature_returns404() {
    Response r = rest.getConfig("does-not-exist");

    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(AdminConfigRest.PROBLEM_TYPE_UNKNOWN_FEATURE, problem.type());
    assertEquals(404, problem.status());
    assertTrue(problem.detail().contains("does-not-exist"),
      "error detail should mention the unknown feature name");
  }

  // ─── PATCH: success path ─────────────────────────────────────────────────

  @Test
  void patchConfig_knownFeature_returns200WithPatchedPayload() throws Exception {
    ConfigDescriptor desc = mock(ConfigDescriptor.class);
    when(desc.featureName()).thenReturn("my-feature");
    JsonNode body = mock(JsonNode.class);
    when(desc.patch(body)).thenReturn("patched-payload");
    registry.register(desc);

    Response r = rest.patchConfig("my-feature", body);

    assertEquals(200, r.getStatus());
    assertEquals("patched-payload", r.getEntity());
  }

  // ─── PATCH: validation failure → 400 ────────────────────────────────────

  @Test
  void patchConfig_validationFailure_returns400ProblemJson() throws Exception {
    ConfigDescriptor desc = mock(ConfigDescriptor.class);
    when(desc.featureName()).thenReturn("my-feature");
    when(desc.patch(null)).thenThrow(
      new ConfigValidationException(
        "/problems/test.bad-field",
        "Invalid field",
        "field must be positive"
      )
    );
    registry.register(desc);

    Response r = rest.patchConfig("my-feature", null);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals("/problems/test.bad-field", problem.type());
    assertEquals("Invalid field", problem.title());
    assertEquals("field must be positive", problem.detail());
    assertEquals(400, problem.status());
  }

  // ─── PATCH: unknown feature → 404 ───────────────────────────────────────

  @Test
  void patchConfig_unknownFeature_returns404() {
    Response r = rest.patchConfig("no-such-feature", null);

    assertEquals(404, r.getStatus());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(AdminConfigRest.PROBLEM_TYPE_UNKNOWN_FEATURE, problem.type());
    assertTrue(problem.detail().contains("no-such-feature"));
  }

  // ─── registry: null / blank featureName silently dropped ────────────────

  @Test
  void register_null_isDropped() {
    registry.register(null);
    // still resolves to empty — no NPE
    assertTrue(registry.find("anything").isEmpty());
  }

  @Test
  void register_blankFeatureName_isDropped() {
    ConfigDescriptor desc = mock(ConfigDescriptor.class);
    when(desc.featureName()).thenReturn("  ");
    registry.register(desc);
    assertTrue(registry.find("  ").isEmpty());
  }

  // ─── registry: featureNames() lists registered entries sorted ────────────

  @Test
  void featureNames_returnsSortedList() {
    for (String name : new String[]{"zzz", "aaa", "mmm"}) {
      ConfigDescriptor d = mock(ConfigDescriptor.class);
      when(d.featureName()).thenReturn(name);
      registry.register(d);
    }
    assertEquals(java.util.List.of("aaa", "mmm", "zzz"), registry.featureNames());
  }

  // ─── 404 detail mentions all known features ──────────────────────────────

  @Test
  void getConfig_404Detail_mentionsKnownFeatures() {
    ConfigDescriptor desc = mock(ConfigDescriptor.class);
    when(desc.featureName()).thenReturn("known-feature");
    registry.register(desc);

    Response r = rest.getConfig("unknown");

    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertTrue(problem.detail().contains("known-feature"),
      "404 detail should list registered features to help the caller");
  }

  // ─── PATCH does not call read() on success ────────────────────────────────

  @Test
  void patchConfig_success_doesNotCallRead() throws Exception {
    ConfigDescriptor desc = mock(ConfigDescriptor.class);
    when(desc.featureName()).thenReturn("feat");
    when(desc.patch(null)).thenReturn("result");
    registry.register(desc);

    rest.patchConfig("feat", null);

    verify(desc, never()).read();
  }
}
