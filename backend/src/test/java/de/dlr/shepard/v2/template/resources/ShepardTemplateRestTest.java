package de.dlr.shepard.v2.template.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
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
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void listReturns401IfUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.list(null, false, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void listExcludesRetiredByDefault() {
    when(dao.list(null, false)).thenReturn(List.of(new ShepardTemplate("recipe", "EXPERIMENT_RECIPE", "{}")));
    Response r = resource.list(null, false, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<ShepardTemplateIO> rows = (List<ShepardTemplateIO>) r.getEntity();
    assertEquals(1, rows.size());
  }

  @Test
  void listIncludeRetiredIgnoredForNonAdmin() {
    when(securityContext.isUserInRole("instance-admin")).thenReturn(false);
    when(dao.list(null, false)).thenReturn(List.of());
    resource.list(null, true, securityContext);
    verify(dao).list(null, false); // includeRetired=true downgraded to false
    verify(dao, never()).list(null, true);
  }

  @Test
  void listIncludeRetiredHonouredForAdmin() {
    when(securityContext.isUserInRole("instance-admin")).thenReturn(true);
    when(dao.list("EXPERIMENT_RECIPE", true)).thenReturn(List.of());
    resource.list("EXPERIMENT_RECIPE", true, securityContext);
    verify(dao).list("EXPERIMENT_RECIPE", true);
  }

  @Test
  void getReturns404WhenMissing() {
    when(dao.findByAppId("ghost")).thenReturn(Optional.empty());
    Response r = resource.get("ghost", securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getReturnsTemplate() {
    var t = new ShepardTemplate("Hot fire recipe", "EXPERIMENT_RECIPE", "{}");
    t.setAppId("app-id-1");
    when(dao.findByAppId("app-id-1")).thenReturn(Optional.of(t));
    Response r = resource.get("app-id-1", securityContext);
    assertEquals(200, r.getStatus());
    var io = (ShepardTemplateIO) r.getEntity();
    assertEquals("Hot fire recipe", io.getName());
  }

  @Test
  void createReturns400WhenBodyMissingRequiredFields() {
    Response r = resource.create(new CreateShepardTemplateIO(null, "EXPERIMENT_RECIPE", "{}", null, null), securityContext);
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
    var body = new CreateShepardTemplateIO("Recipe", "EXPERIMENT_RECIPE", "{\"x\":1}", "desc", List.of("lumen"));
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
    Response r = resource.patch("ghost", new PatchShepardTemplateIO("new", null, null, null), securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void patchTriggersCopyOnWriteAndRetiresPrior() {
    var prior = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"x\":1}");
    prior.setAppId("prior-appid");
    prior.setVersion(1);
    when(dao.findByAppId("prior-appid")).thenReturn(Optional.of(prior));
    when(dao.nextVersionOf(prior)).thenAnswer(inv -> {
      ShepardTemplate next = new ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"x\":1}");
      next.setVersion(2);
      return next;
    });
    when(dao.createOrUpdate(any(ShepardTemplate.class))).thenAnswer(inv -> {
      ShepardTemplate t = inv.getArgument(0);
      if (t.getAppId() == null) t.setAppId("new-appid");
      return t;
    });

    var body = new PatchShepardTemplateIO(null, "{\"x\":2}", null, null);
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
    assertEquals("{\"x\":2}", newRow.getBody());
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
    Response r = resource.tags(null, securityContext);
    assertEquals(401, r.getStatus());
    verify(dao, never()).listDistinctTags(any());
  }

  @Test
  void tagsReturnsDistinctList() {
    when(dao.listDistinctTags(null)).thenReturn(List.of("calibration", "hot-fire", "lumen"));
    Response r = resource.tags(null, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<String> tags = (List<String>) r.getEntity();
    assertEquals(3, tags.size());
    assertEquals("calibration", tags.get(0));
  }

  @Test
  void tagsHonoursKindFilter() {
    when(dao.listDistinctTags("EXPERIMENT_RECIPE")).thenReturn(List.of("hot-fire"));
    Response r = resource.tags("EXPERIMENT_RECIPE", securityContext);
    assertEquals(200, r.getStatus());
    verify(dao).listDistinctTags("EXPERIMENT_RECIPE");
  }
}
