package de.dlr.shepard.v2.template.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateBodyValidator;
import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link TemplatePortabilityRest} (T1f).
 *
 * <p>Covers the auth gates (401 unauthenticated / 403 non-admin
 * via the manual principal-check guard), happy-path export and
 * import, error paths (bad YAML, body-validation failure), and
 * the round-trip: export → YAML string → import creates new rows.
 *
 * <p>The {@code @RolesAllowed} annotation is container-enforced and
 * is not tested here; 403 is tested at the JAX-RS security filter
 * level. The 401 paths use the explicit
 * {@code securityContext.getUserPrincipal() == null} guard that
 * mirrors the pattern in {@code ShepardTemplateRest.list()}.
 */
class TemplatePortabilityRestTest {

  static final String ADMIN = "admin-user";

  @Mock
  ShepardTemplateDAO dao;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  TemplatePortabilityRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TemplatePortabilityRest();
    resource.dao = dao;
    resource.bodyValidator = new TemplateBodyValidator();

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(ADMIN);
  }

  // -----------------------------------------------------------------------
  // Export — auth gates
  // -----------------------------------------------------------------------

  @Test
  void exportReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.export(false, securityContext);
    assertEquals(401, r.getStatus());
    verify(dao, never()).list(any(), any(boolean.class));
  }

  // -----------------------------------------------------------------------
  // Export — happy path
  // -----------------------------------------------------------------------

  @Test
  void exportReturnsYamlWithCorrectContentType() {
    when(dao.list(null, false)).thenReturn(List.of());
    Response r = resource.export(false, securityContext);
    assertEquals(200, r.getStatus());
    String ct = r.getHeaderString("Content-Type");
    assertNotNull(ct);
    assertTrue(ct.startsWith("text/yaml"), "Expected text/yaml content-type, got: " + ct);
  }

  @Test
  void exportSetsContentDispositionHeader() {
    when(dao.list(null, false)).thenReturn(List.of());
    Response r = resource.export(false, securityContext);
    String cd = r.getHeaderString("Content-Disposition");
    assertNotNull(cd);
    assertTrue(cd.contains("attachment"), "Expected attachment content-disposition, got: " + cd);
    assertTrue(cd.contains("shepard-templates.yaml"), "Expected filename in content-disposition, got: " + cd);
  }

  @Test
  void exportIncludesTemplateFields() {
    ShepardTemplate t = new ShepardTemplate("Hot fire", "EXPERIMENT_RECIPE", "{\"experiment\":{\"steps\":[]}}");
    t.setAppId("app-id-1");
    t.setVersion(1);
    t.setDescription("A hot fire recipe");
    t.setTags(List.of("lumen", "hot-fire"));

    when(dao.list(null, false)).thenReturn(List.of(t));
    Response r = resource.export(false, securityContext);

    assertEquals(200, r.getStatus());
    String yaml = (String) r.getEntity();
    assertNotNull(yaml);
    assertTrue(yaml.contains("Hot fire"), "YAML should contain template name");
    assertTrue(yaml.contains("EXPERIMENT_RECIPE"), "YAML should contain templateKind");
    assertTrue(yaml.contains("A hot fire recipe"), "YAML should contain description");
    assertTrue(yaml.contains("lumen"), "YAML should contain tags");
    assertTrue(yaml.contains("experiment"), "YAML should contain body content");
  }

  @Test
  void exportDoesNotIncludeAppIdButIncludesVersion() {
    ShepardTemplate t = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    t.setAppId("should-not-appear-appid");
    t.setVersion(99);

    when(dao.list(null, false)).thenReturn(List.of(t));
    Response r = resource.export(false, securityContext);

    String yaml = (String) r.getEntity();
    assertFalse(yaml.contains("should-not-appear-appid"), "appId should not be in export YAML");
    // version is informational metadata included in the export document
    assertTrue(yaml.contains("99"), "version should be present in export YAML as informational metadata");
  }

  @Test
  void exportWithIncludeRetiredPassesTrueToDao() {
    when(dao.list(null, true)).thenReturn(List.of());
    resource.export(true, securityContext);
    verify(dao).list(null, true);
  }

  @Test
  void exportWithoutIncludeRetiredPassesFalseToDao() {
    when(dao.list(null, false)).thenReturn(List.of());
    resource.export(false, securityContext);
    verify(dao).list(null, false);
  }

  // -----------------------------------------------------------------------
  // Import — auth gates
  // -----------------------------------------------------------------------

  @Test
  void importReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.importTemplates("- name: x\n  templateKind: y\n  body: '{}'\n", securityContext);
    assertEquals(401, r.getStatus());
    verify(dao, never()).createOrUpdate(any());
  }

  // -----------------------------------------------------------------------
  // Import — bad input
  // -----------------------------------------------------------------------

  @Test
  void importReturns400OnMalformedYaml() {
    // Deliberately broken YAML (tab-indented list that triggers parse error)
    String badYaml = "- name: ok\n\t  templateKind: not_valid_yaml_indent";
    Response r = resource.importTemplates(badYaml, securityContext);
    assertEquals(400, r.getStatus());
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> body = (java.util.Map<String, Object>) r.getEntity();
    assertNotNull(body.get("error"));
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void importReturns400WhenEntryMissingRequiredFields() {
    // Missing 'body' field
    String yaml = "- name: Recipe\n  templateKind: EXPERIMENT_RECIPE\n";
    Response r = resource.importTemplates(yaml, securityContext);
    assertEquals(400, r.getStatus());
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> body = (java.util.Map<String, Object>) r.getEntity();
    assertTrue(body.get("error").toString().contains("body are required"));
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void importReturns400OnBodyValidationFailure() {
    // body does not contain the expected keys for EXPERIMENT_RECIPE
    String yaml =
      "- name: Recipe\n" +
      "  templateKind: EXPERIMENT_RECIPE\n" +
      "  body: '{\"collection\":{}}'\n";
    Response r = resource.importTemplates(yaml, securityContext);
    assertEquals(400, r.getStatus());
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> body = (java.util.Map<String, Object>) r.getEntity();
    assertTrue(body.get("error").toString().contains("body invalid"));
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void importReturns200WithEmptyListForNullInput() {
    // null body is treated as empty document — returns 200 with empty list.
    Response r = resource.importTemplates(null, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<?> result = (List<?>) r.getEntity();
    assertTrue(result.isEmpty(), "Expected empty list for null input");
  }

  @Test
  void importReturns200WithEmptyListForEmptyYaml() {
    // Explicitly empty YAML "[]" is valid — returns 200 with empty list.
    Response r = resource.importTemplates("[]", securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<?> result = (List<?>) r.getEntity();
    assertTrue(result.isEmpty(), "Expected empty list for empty YAML input");
  }

  // -----------------------------------------------------------------------
  // Import — happy path (new template)
  // -----------------------------------------------------------------------

  @Test
  void importMintsNewTemplateWhenNoCollisionExists() {
    String yaml =
      "- name: Hot fire\n" +
      "  templateKind: EXPERIMENT_RECIPE\n" +
      "  description: A recipe\n" +
      "  tags:\n" +
      "    - lumen\n" +
      "    - test\n" +
      "  body: '{\"experiment\":{\"steps\":[]}}'\n";

    when(dao.findLatestByName("Hot fire", "EXPERIMENT_RECIPE")).thenReturn(Optional.empty());
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate t = inv.getArgument(0);
      t.setAppId("new-app-id");
      return t;
    });

    Response r = resource.importTemplates(yaml, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<ShepardTemplateIO> result = (List<ShepardTemplateIO>) r.getEntity();
    assertEquals(1, result.size());
    assertEquals("Hot fire", result.get(0).getName());
    assertEquals("EXPERIMENT_RECIPE", result.get(0).getTemplateKind());
    assertEquals(ADMIN, result.get(0).getCreatedBy());
    assertEquals(1, result.get(0).getVersion());

    verify(dao, times(1)).createOrUpdate(any(ShepardTemplate.class));
  }

  @Test
  void importSetsCreatedByToCallerNotExportedValue() {
    String yaml =
      "- name: Recipe\n" +
      "  templateKind: EXPERIMENT_RECIPE\n" +
      "  body: '{\"experiment\":{\"steps\":[]}}'\n";

    when(dao.findLatestByName("Recipe", "EXPERIMENT_RECIPE")).thenReturn(Optional.empty());
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate t = inv.getArgument(0);
      t.setAppId("x");
      return t;
    });

    resource.importTemplates(yaml, securityContext);

    ArgumentCaptor<ShepardTemplate> captor = ArgumentCaptor.forClass(ShepardTemplate.class);
    verify(dao).createOrUpdate(captor.capture());
    assertEquals(ADMIN, captor.getValue().getCreatedBy());
  }

  // -----------------------------------------------------------------------
  // Import — copy-on-write when name already exists
  // -----------------------------------------------------------------------

  @Test
  void importCreatesCopyOnWriteVersionWhenNameExists() {
    String yaml =
      "- name: Existing\n" +
      "  templateKind: EXPERIMENT_RECIPE\n" +
      "  body: '{\"experiment\":{\"steps\":[]}}'\n";

    ShepardTemplate prior = new ShepardTemplate("Existing", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    prior.setAppId("prior-id");
    prior.setVersion(1);

    when(dao.findLatestByName("Existing", "EXPERIMENT_RECIPE")).thenReturn(Optional.of(prior));
    when(dao.nextVersionOf(prior)).thenAnswer(inv -> {
      ShepardTemplate next = new ShepardTemplate("Existing", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
      next.setVersion(2);
      return next;
    });
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate t = inv.getArgument(0);
      if (t.getAppId() == null) t.setAppId("new-v2-id");
      return t;
    });

    Response r = resource.importTemplates(yaml, securityContext);

    assertEquals(200, r.getStatus());

    // createOrUpdate should be called twice: once to retire prior, once to save next
    ArgumentCaptor<ShepardTemplate> captor = ArgumentCaptor.forClass(ShepardTemplate.class);
    verify(dao, times(2)).createOrUpdate(captor.capture());

    ShepardTemplate retiredCall = captor.getAllValues().get(0);
    assertEquals("prior-id", retiredCall.getAppId());
    assertTrue(retiredCall.isRetired());

    ShepardTemplate nextVersion = captor.getAllValues().get(1);
    assertEquals(2, nextVersion.getVersion());
    assertFalse(nextVersion.isRetired());

    @SuppressWarnings("unchecked")
    List<ShepardTemplateIO> result = (List<ShepardTemplateIO>) r.getEntity();
    assertEquals(1, result.size());
    assertEquals(2, result.get(0).getVersion());
  }

  // -----------------------------------------------------------------------
  // Round-trip: export → YAML string → import
  // -----------------------------------------------------------------------

  @Test
  void roundTripExportThenImportPreservesFields() {
    // Seed one template for export.
    ShepardTemplate t = new ShepardTemplate("Round trip", "EXPERIMENT_RECIPE", "{\"experiment\":{\"steps\":[]}}");
    t.setAppId("rt-appid");
    t.setVersion(1);
    t.setDescription("Round-trip description");
    t.setTags(List.of("rt", "test"));

    when(dao.list(null, false)).thenReturn(List.of(t));

    // Export.
    Response exportResponse = resource.export(false, securityContext);
    assertEquals(200, exportResponse.getStatus());
    String yaml = (String) exportResponse.getEntity();

    // Wire up mocks for the subsequent import.
    when(dao.findLatestByName("Round trip", "EXPERIMENT_RECIPE")).thenReturn(Optional.empty());
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate s = inv.getArgument(0);
      s.setAppId("new-rt-appid");
      return s;
    });

    // Import the exported YAML.
    Response importResponse = resource.importTemplates(yaml, securityContext);
    assertEquals(200, importResponse.getStatus());

    @SuppressWarnings("unchecked")
    List<ShepardTemplateIO> imported = (List<ShepardTemplateIO>) importResponse.getEntity();
    assertEquals(1, imported.size());

    ShepardTemplateIO io = imported.get(0);
    assertEquals("Round trip", io.getName());
    assertEquals("EXPERIMENT_RECIPE", io.getTemplateKind());
    assertEquals("Round-trip description", io.getDescription());
    assertEquals(List.of("rt", "test"), io.getTags());
    assertTrue(io.getBody().contains("experiment"), "Body should contain exported JSON content");

    // appId must NOT be the original — import always mints a new one.
    assertEquals("new-rt-appid", io.getAppId());
  }
}
