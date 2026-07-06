package de.dlr.shepard.v2.template.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateBodyValidator;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.template.io.CreateShepardTemplateIO;
import de.dlr.shepard.v2.template.io.PatchShepardTemplateIO;
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

class ShepardTemplateRestTest {

  static final String CALLER = "admin";

  @Mock
  ShepardTemplateDAO dao;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  ShepardTemplateRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ShepardTemplateRest();
    resource.dao = dao;
    resource.bodyValidator = new TemplateBodyValidator();
    resource.inheritanceResolver = new TemplateInheritanceResolver(dao);
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void listReturns401IfUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.list(null, false, 0, 50, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void listExcludesRetiredByDefault() {
    when(dao.count(null, false)).thenReturn(1L);
    when(dao.list(null, false, 0, 50)).thenReturn(List.of(new ShepardTemplate("recipe", "EXPERIMENT_RECIPE", "{}")));
    Response r = resource.list(null, false, 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<ShepardTemplateIO> page = (PagedResponseIO<ShepardTemplateIO>) r.getEntity();
    assertEquals(1, page.items().size());
    assertEquals(1L, page.total());
    assertEquals(0, page.page());
    assertEquals(50, page.pageSize());
  }

  @Test
  void listIncludeRetiredIgnoredForNonAdmin() {
    when(securityContext.isUserInRole("instance-admin")).thenReturn(false);
    when(dao.count(null, false)).thenReturn(0L);
    when(dao.list(null, false, 0, 50)).thenReturn(List.of());
    resource.list(null, true, 0, 50, securityContext);
    verify(dao).list(null, false, 0, 50); // includeRetired=true downgraded to false
    verify(dao, never()).list(null, true, 0, 50);
  }

  @Test
  void listIncludeRetiredHonouredForAdmin() {
    when(securityContext.isUserInRole("instance-admin")).thenReturn(true);
    when(dao.count("EXPERIMENT_RECIPE", true)).thenReturn(0L);
    when(dao.list("EXPERIMENT_RECIPE", true, 0, 50)).thenReturn(List.of());
    resource.list("EXPERIMENT_RECIPE", true, 0, 50, securityContext);
    verify(dao).list("EXPERIMENT_RECIPE", true, 0, 50);
  }

  @Test
  void listPageEnvelopeFieldsAreCorrect() {
    when(dao.count(null, false)).thenReturn(75L);
    when(dao.list(null, false, 1, 20)).thenReturn(List.of());
    Response r = resource.list(null, false, 1, 20, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<ShepardTemplateIO> page = (PagedResponseIO<ShepardTemplateIO>) r.getEntity();
    assertEquals(75L, page.total());
    assertEquals(1, page.page());
    assertEquals(20, page.pageSize());
  }

  @Test
  void listPage1CallsCorrectDaoParams() {
    when(dao.count(null, false)).thenReturn(10L);
    when(dao.list(null, false, 1, 5)).thenReturn(List.of());
    resource.list(null, false, 1, 5, securityContext);
    verify(dao).count(null, false);
    verify(dao).list(null, false, 1, 5);
  }

  @Test
  void listKindFilterPassedToDao() {
    when(dao.count("MAPPING_RECIPE", false)).thenReturn(3L);
    when(dao.list("MAPPING_RECIPE", false, 0, 50)).thenReturn(List.of());
    resource.list("MAPPING_RECIPE", false, 0, 50, securityContext);
    verify(dao).count("MAPPING_RECIPE", false);
    verify(dao).list("MAPPING_RECIPE", false, 0, 50);
  }

  @Test
  void getReturns404WhenMissing() {
    when(dao.findByAppId("ghost")).thenReturn(Optional.empty());
    Response r = resource.get("ghost", false, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getReturnsTemplate() {
    var t = new ShepardTemplate("Hot fire recipe", "EXPERIMENT_RECIPE", "{}");
    t.setAppId("app-id-1");
    when(dao.findByAppId("app-id-1")).thenReturn(Optional.of(t));
    Response r = resource.get("app-id-1", false, securityContext);
    assertEquals(200, r.getStatus());
    var io = (ShepardTemplateIO) r.getEntity();
    assertEquals("Hot fire recipe", io.getName());
  }

  @Test
  void createReturns400WhenBodyMissingRequiredFields() {
    Response r = resource.create(new CreateShepardTemplateIO(null, "EXPERIMENT_RECIPE", "{}", null, null, null, null), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createReturns400OnNullBody() {
    Response r = resource.create(null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createMintsRow() {
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate t = inv.getArgument(0);
      t.setAppId("server-minted-appid");
      return t;
    });
    var body = new CreateShepardTemplateIO("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{\"steps\":[]}}", "desc", List.of("lumen"), null, null);
    Response r = resource.create(body, securityContext);
    assertEquals(201, r.getStatus());
    var io = (ShepardTemplateIO) r.getEntity();
    assertEquals("Recipe", io.getName());
    assertEquals("EXPERIMENT_RECIPE", io.getTemplateKind());
    assertEquals(1, io.getVersion());
    assertEquals(CALLER, io.getCreatedBy());
    assertNotNull(io.getCreatedAt());
  }

  @Test
  void patchReturns404WhenMissing() {
    when(dao.findByAppId("ghost")).thenReturn(Optional.empty());
    Response r = resource.patch("ghost", new PatchShepardTemplateIO("new", null, null, null, null, null), securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void patchTriggersCopyOnWriteAndRetiresPrior() {
    var prior = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    prior.setAppId("prior-appid");
    prior.setVersion(1);
    when(dao.findByAppId("prior-appid")).thenReturn(Optional.of(prior));
    when(dao.nextVersionOf(prior)).thenAnswer(inv -> {
      ShepardTemplate next = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
      next.setVersion(2);
      return next;
    });
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate t = inv.getArgument(0);
      if (t.getAppId() == null) t.setAppId("new-appid");
      return t;
    });

    var body = new PatchShepardTemplateIO(null, "{\"experiment\":{\"v\":2}}", null, null, null, null);
    Response r = resource.patch("prior-appid", body, securityContext);

    assertEquals(200, r.getStatus());
    // dao.createOrUpdate called twice — once for prior (retire), once for new.
    ArgumentCaptor<ShepardTemplate> captor = ArgumentCaptor.forClass(ShepardTemplate.class);
    verify(dao, times(2)).createOrUpdate(captor.capture());

    var saved = captor.getAllValues();
    var retired = saved.get(0);
    assertEquals("prior-appid", retired.getAppId());
    assertTrue(retired.isRetired());

    var newRow = saved.get(1);
    assertEquals(Integer.valueOf(2), newRow.getVersion());
    assertEquals("{\"experiment\":{\"v\":2}}", newRow.getBody());
  }

  @Test
  void retireSoftDeletesRow() {
    var t = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{}");
    t.setAppId("app-id-1");
    when(dao.findByAppId("app-id-1")).thenReturn(Optional.of(t));
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

    Response r = resource.retire("app-id-1", securityContext);

    assertEquals(204, r.getStatus());
    ArgumentCaptor<ShepardTemplate> captor = ArgumentCaptor.forClass(ShepardTemplate.class);
    verify(dao).createOrUpdate(captor.capture());
    assertTrue(captor.getValue().isRetired());
  }

  @Test
  void retireReturns404WhenMissing() {
    when(dao.findByAppId("ghost")).thenReturn(Optional.empty());
    Response r = resource.retire("ghost", securityContext);
    assertEquals(404, r.getStatus());
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void tagsReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.tags(null, 0, 50, securityContext);
    assertEquals(401, r.getStatus());
    verify(dao, never()).listDistinctTags(any());
  }

  @Test
  void tagsReturnsDistinctList() {
    when(dao.listDistinctTags(null)).thenReturn(List.of("calibration", "hot-fire", "lumen"));
    Response r = resource.tags(null, 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    de.dlr.shepard.v2.common.io.PagedResponseIO<String> paged =
        (de.dlr.shepard.v2.common.io.PagedResponseIO<String>) r.getEntity();
    assertEquals(3L, paged.total());
    assertEquals(3, paged.items().size());
    assertEquals("calibration", paged.items().get(0));
    assertEquals("3", r.getHeaderString("X-Total-Count"));
  }

  @Test
  void tagsHonoursKindFilter() {
    when(dao.listDistinctTags("EXPERIMENT_RECIPE")).thenReturn(List.of("hot-fire"));
    Response r = resource.tags("EXPERIMENT_RECIPE", 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    verify(dao).listDistinctTags("EXPERIMENT_RECIPE");
  }

  @Test
  void tagsPaginatesInMemory() {
    when(dao.listDistinctTags(null)).thenReturn(List.of("a", "b", "c"));
    Response page0 = resource.tags(null, 0, 2, securityContext);
    assertEquals(200, page0.getStatus());
    @SuppressWarnings("unchecked")
    de.dlr.shepard.v2.common.io.PagedResponseIO<String> p0 =
        (de.dlr.shepard.v2.common.io.PagedResponseIO<String>) page0.getEntity();
    assertEquals(3L, p0.total());
    assertEquals(2, p0.items().size());
    assertEquals("a", p0.items().get(0));

    when(dao.listDistinctTags(null)).thenReturn(List.of("a", "b", "c"));
    Response page1 = resource.tags(null, 1, 2, securityContext);
    @SuppressWarnings("unchecked")
    de.dlr.shepard.v2.common.io.PagedResponseIO<String> p1 =
        (de.dlr.shepard.v2.common.io.PagedResponseIO<String>) page1.getEntity();
    assertEquals(1, p1.items().size());
    assertEquals("c", p1.items().get(0));
  }

  @Test
  void createReturns400OnMalformedJson() {
    var body = new CreateShepardTemplateIO("Recipe", "EXPERIMENT_RECIPE", "{ not valid json", null, null, null, null);
    Response r = resource.create(body, securityContext);
    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertEquals("/problems/templates.bad-request", problem.type());
    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) problem.extensions().get("errors");
    assertTrue(errors.get(0).contains("not valid JSON"));
  }

  @Test
  void createReturns400OnWrongShapeBodyForKind() {
    var body = new CreateShepardTemplateIO("Recipe", "EXPERIMENT_RECIPE", "{\"collection\":{}}", null, null, null, null);
    Response r = resource.create(body, securityContext);
    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertEquals("/problems/templates.bad-request", problem.type());
    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) problem.extensions().get("errors");
    assertTrue(errors.get(0).contains("templateKind=EXPERIMENT_RECIPE"));
  }

  // ────────────────────────────────────────────────────────────────────
  // TEMPLATE-ICONS-1 — iconKey lifecycle through create + patch (COW)
  // ────────────────────────────────────────────────────────────────────

  @Test
  void createCarriesIconKeyThroughToEntity() {
    ArgumentCaptor<ShepardTemplate> captor = ArgumentCaptor.forClass(ShepardTemplate.class);
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate t = inv.getArgument(0);
      t.setAppId("server-minted-appid");
      return t;
    });
    var body = new CreateShepardTemplateIO(
      "Recipe",
      "EXPERIMENT_RECIPE",
      "{\"experiment\":{\"steps\":[]}}",
      null,
      null,
      "mdi-layers",
      null
    );
    Response r = resource.create(body, securityContext);
    assertEquals(201, r.getStatus());
    verify(dao).createOrUpdate(captor.capture());
    assertEquals("mdi-layers", captor.getValue().getIconKey());
    assertEquals("mdi-layers", ((ShepardTemplateIO) r.getEntity()).getIconKey());
  }

  @Test
  void patchUpdatesIconKeyAndPreservesOnNewVersion() {
    var prior = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    prior.setAppId("prior-appid");
    prior.setVersion(1);
    prior.setIconKey("mdi-circle-medium");
    when(dao.findByAppId("prior-appid")).thenReturn(Optional.of(prior));
    when(dao.nextVersionOf(prior)).thenAnswer(inv -> {
      // Simulate the real DAO behaviour — iconKey is carried through on COW.
      ShepardTemplate next = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
      next.setVersion(2);
      next.setIconKey(prior.getIconKey());
      return next;
    });
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

    var body = new PatchShepardTemplateIO(null, null, null, null, "mdi-layers", null);
    Response r = resource.patch("prior-appid", body, securityContext);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<ShepardTemplate> captor = ArgumentCaptor.forClass(ShepardTemplate.class);
    verify(dao, times(2)).createOrUpdate(captor.capture());
    var newRow = captor.getAllValues().get(1);
    assertEquals("mdi-layers", newRow.getIconKey());
  }

  @Test
  void patchEmptyStringClearsIconKey() {
    var prior = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    prior.setAppId("prior-appid");
    prior.setVersion(1);
    prior.setIconKey("mdi-layers");
    when(dao.findByAppId("prior-appid")).thenReturn(Optional.of(prior));
    when(dao.nextVersionOf(prior)).thenAnswer(inv -> {
      ShepardTemplate next = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
      next.setVersion(2);
      next.setIconKey(prior.getIconKey());
      return next;
    });
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

    var body = new PatchShepardTemplateIO(null, null, null, null, "", null);
    Response r = resource.patch("prior-appid", body, securityContext);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<ShepardTemplate> captor = ArgumentCaptor.forClass(ShepardTemplate.class);
    verify(dao, times(2)).createOrUpdate(captor.capture());
    var newRow = captor.getAllValues().get(1);
    org.junit.jupiter.api.Assertions.assertNull(newRow.getIconKey());
  }

  @Test
  void patchOmittingIconKeyPreservesPriorValueViaCow() {
    var prior = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    prior.setAppId("prior-appid");
    prior.setVersion(1);
    prior.setIconKey("mdi-layers");
    when(dao.findByAppId("prior-appid")).thenReturn(Optional.of(prior));
    when(dao.nextVersionOf(prior)).thenAnswer(inv -> {
      ShepardTemplate next = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
      next.setVersion(2);
      next.setIconKey(prior.getIconKey()); // mimic DAO COW
      return next;
    });
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

    // Only name patched; iconKey omitted ⇒ COW preserves "mdi-layers".
    var body = new PatchShepardTemplateIO("Renamed Recipe", null, null, null, null, null);
    Response r = resource.patch("prior-appid", body, securityContext);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<ShepardTemplate> captor = ArgumentCaptor.forClass(ShepardTemplate.class);
    verify(dao, times(2)).createOrUpdate(captor.capture());
    var newRow = captor.getAllValues().get(1);
    assertEquals("mdi-layers", newRow.getIconKey());
  }

  @Test
  void patchReturns400OnMalformedBody() {
    var prior = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    prior.setAppId("prior-appid");
    when(dao.findByAppId("prior-appid")).thenReturn(Optional.of(prior));

    var body = new PatchShepardTemplateIO(null, "[]", null, null, null, null);
    Response r = resource.patch("prior-appid", body, securityContext);

    assertEquals(400, r.getStatus());
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void createRejectsMissingParent() {
    when(dao.findByAppId("missing-parent")).thenReturn(Optional.empty());
    var body = new CreateShepardTemplateIO("Child", "EXPERIMENT_RECIPE", "{\"experiment\":{}}", null, null, null, "missing-parent");
    Response r = resource.create(body, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createRejectsParentOfDifferentKind() {
    var parent = new ShepardTemplate("Parent", "DATAOBJECT_RECIPE", "{}");
    parent.setAppId("parent-appid");
    when(dao.findByAppId("parent-appid")).thenReturn(Optional.of(parent));
    var body = new CreateShepardTemplateIO("Child", "EXPERIMENT_RECIPE", "{\"experiment\":{}}", null, null, null, "parent-appid");
    Response r = resource.create(body, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createSetsParentWhenValid() {
    var parent = new ShepardTemplate("Parent", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    parent.setAppId("parent-appid");
    when(dao.findByAppId("parent-appid")).thenReturn(Optional.of(parent));
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate t = inv.getArgument(0);
      t.setAppId("child-appid");
      return t;
    });
    var body = new CreateShepardTemplateIO("Child", "EXPERIMENT_RECIPE", "{\"experiment\":{}}", null, null, null, "parent-appid");
    Response r = resource.create(body, securityContext);
    assertEquals(201, r.getStatus());
    var io = (ShepardTemplateIO) r.getEntity();
    assertEquals("parent-appid", io.getParentTemplateAppId());
  }

  @Test
  void patchRejectsSelfParentCycle() {
    var prior = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    prior.setAppId("self-appid");
    prior.setVersion(1);
    when(dao.findByAppId("self-appid")).thenReturn(Optional.of(prior));
    var body = new PatchShepardTemplateIO(null, null, null, null, null, "self-appid");
    Response r = resource.patch("self-appid", body, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void getFlattenMergesParentBodyChildWins() {
    var parent = new ShepardTemplate("Parent", "DATAOBJECT_RECIPE",
      "{\"dataobjects\":[{\"attributes\":{\"bench\":\"P1\",\"shift\":\"A\"}}]}");
    parent.setAppId("parent-appid");
    parent.setIconKey("mdi-layers");
    var child = new ShepardTemplate("Child", "DATAOBJECT_RECIPE",
      "{\"dataobjects\":[{\"attributes\":{\"shift\":\"B\"}}]}");
    child.setAppId("child-appid");
    child.setParentTemplateAppId("parent-appid");
    when(dao.findByAppId("child-appid")).thenReturn(Optional.of(child));
    when(dao.findByAppId("parent-appid")).thenReturn(Optional.of(parent));

    Response r = resource.get("child-appid", true, securityContext);
    assertEquals(200, r.getStatus());
    var io = (ShepardTemplateIO) r.getEntity();
    // child overrides shift=B; bench inherited from parent; iconKey inherited.
    assertTrue(io.getBody().contains("\"shift\":\"B\""));
    assertTrue(io.getBody().contains("\"bench\":\"P1\""));
    assertEquals("mdi-layers", io.getIconKey());
  }
}
