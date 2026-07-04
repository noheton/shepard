package de.dlr.shepard.v2.admin.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.daos.OntologyGitSourceDAO;
import de.dlr.shepard.context.semantic.entities.OntologyGitSource;
import de.dlr.shepard.context.semantic.services.OntologyGitIngestService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.admin.semantic.io.OntologyGitSourceIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class OntologyGitSourceRestTest {

  @Mock OntologyGitSourceDAO gitSourceDAO;
  @Mock OntologyGitIngestService ingestService;
  @Mock AuthenticationContext authenticationContext;
  @Mock SecurityContext securityContext;
  @Mock Principal principal;

  OntologyGitSourceRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new OntologyGitSourceRest();
    resource.gitSourceDAO = gitSourceDAO;
    resource.ingestService = ingestService;
    resource.authenticationContext = authenticationContext;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("admin");
    when(securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);
  }

  // ─── list ────────────────────────────────────────────────────────────────

  @Test
  void listReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.list(0, 50, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void listReturns200WithXTotalCount() {
    when(gitSourceDAO.count()).thenReturn(2L);
    when(gitSourceDAO.listPaged(0L, 50)).thenReturn(List.of(makeSource("a1"), makeSource("a2")));
    Response r = resource.list(0, 50, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals(2L, Long.parseLong(r.getHeaderString("X-Total-Count")));
    @SuppressWarnings("unchecked")
    PagedResponseIO<OntologyGitSourceIO> body = (PagedResponseIO<OntologyGitSourceIO>) r.getEntity();
    assertEquals(2, body.items().size());
  }

  @Test
  void listPaginatesFirstPage() {
    when(gitSourceDAO.count()).thenReturn(3L);
    when(gitSourceDAO.listPaged(0L, 2)).thenReturn(List.of(makeSource("a1"), makeSource("a2")));
    Response r = resource.list(0, 2, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals(3L, Long.parseLong(r.getHeaderString("X-Total-Count")));
    @SuppressWarnings("unchecked")
    PagedResponseIO<OntologyGitSourceIO> body = (PagedResponseIO<OntologyGitSourceIO>) r.getEntity();
    assertEquals(2, body.items().size());
    assertEquals("a1", body.items().get(0).getAppId());
  }

  @Test
  void listMaxPageSizeReturnsFirstTwoHundred() {
    List<OntologyGitSource> big = new ArrayList<>();
    for (int i = 0; i < 200; i++) big.add(makeSource("src-" + i));
    when(gitSourceDAO.count()).thenReturn(250L);
    when(gitSourceDAO.listPaged(0L, 200)).thenReturn(big);
    Response r = resource.list(0, 200, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<OntologyGitSourceIO> body = (PagedResponseIO<OntologyGitSourceIO>) r.getEntity();
    assertEquals(200, body.items().size());
    assertEquals(250L, Long.parseLong(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void listPageParamsCarryValidationAnnotations() throws NoSuchMethodException {
    java.lang.reflect.Method m = de.dlr.shepard.v2.admin.semantic.OntologyGitSourceRest.class.getDeclaredMethod(
        "list", int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter page = m.getParameters()[0];
    java.lang.reflect.Parameter size = m.getParameters()[1];
    assertNotNull(page.getAnnotation(jakarta.validation.constraints.PositiveOrZero.class), "page: @PositiveOrZero");
    assertNotNull(size.getAnnotation(jakarta.validation.constraints.Min.class), "pageSize: @Min");
    assertNotNull(size.getAnnotation(jakarta.validation.constraints.Max.class), "pageSize: @Max");
  }

  @Test
  void listReturnsSecondPage() {
    when(gitSourceDAO.count()).thenReturn(3L);
    when(gitSourceDAO.listPaged(2L, 2)).thenReturn(List.of(makeSource("a3")));
    Response r = resource.list(1, 2, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<OntologyGitSourceIO> body = (PagedResponseIO<OntologyGitSourceIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("a3", body.items().get(0).getAppId());
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private static OntologyGitSource makeSource(String appId) {
    OntologyGitSource s = new OntologyGitSource();
    s.setAppId(appId);
    s.setName("source-" + appId);
    s.setRepoUrl("https://example.com/" + appId + ".git");
    s.setBranch("main");
    s.setPathPattern("*.ttl");
    s.setEnabled(true);
    return s;
  }
}
