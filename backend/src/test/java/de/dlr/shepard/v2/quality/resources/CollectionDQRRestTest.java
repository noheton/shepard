package de.dlr.shepard.v2.quality.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.quality.io.DQRIO;
import de.dlr.shepard.v2.quality.services.DataQualityRequirementService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionDQRRestTest {

  static final String COLL_APP_ID = "019e3c96-0000-7000-a000-000000000002";
  static final String ALICE = "alice";

  @Mock
  DataQualityRequirementService service;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  CollectionDQRRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionDQRRest();
    resource.service = service;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(ALICE);
  }

  // ─── GET /dqr ─────────────────────────────────────────────────────────────

  @Test
  void listReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.list(COLL_APP_ID, 0, 50, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).list(anyString(), anyString());
  }

  @Test
  void listReturns200WithXTotalCount() {
    DQRIO dqr = makeDQRIO("dqr-1", "Must have name");
    when(service.list(COLL_APP_ID, ALICE)).thenReturn(List.of(dqr));

    Response r = resource.list(COLL_APP_ID, 0, 50, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DQRIO> body = (List<DQRIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals(1L, Long.parseLong(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void listPropagates403FromService() {
    when(service.list(eq(COLL_APP_ID), eq(ALICE))).thenThrow(new ForbiddenException());
    org.junit.jupiter.api.Assertions.assertThrows(ForbiddenException.class,
      () -> resource.list(COLL_APP_ID, 0, 50, securityContext));
  }

  @Test
  void listPaginatesResultsWhenPageSizeProvided() {
    List<DQRIO> all = List.of(
      makeDQRIO("dqr-1", "Rule A"),
      makeDQRIO("dqr-2", "Rule B"),
      makeDQRIO("dqr-3", "Rule C")
    );
    when(service.list(COLL_APP_ID, ALICE)).thenReturn(all);

    Response r = resource.list(COLL_APP_ID, 0, 2, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DQRIO> body = (List<DQRIO>) r.getEntity();
    assertEquals(2, body.size());
    assertEquals(3L, Long.parseLong(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void listMaxPageSizeReturnsFirstTwoHundred() {
    List<DQRIO> all = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      all.add(makeDQRIO("dqr-" + i, "Rule " + i));
    }
    when(service.list(COLL_APP_ID, ALICE)).thenReturn(all);

    Response r = resource.list(COLL_APP_ID, 0, 200, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DQRIO> body = (List<DQRIO>) r.getEntity();
    assertEquals(200, body.size());
    assertEquals(250L, Long.parseLong(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void listPageParamsCarryValidationAnnotations() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionDQRRest.class.getDeclaredMethod(
        "list", String.class, int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter page = m.getParameters()[1];
    java.lang.reflect.Parameter size = m.getParameters()[2];
    assertNotNull(page.getAnnotation(jakarta.validation.constraints.PositiveOrZero.class), "page: @PositiveOrZero");
    assertNotNull(size.getAnnotation(jakarta.validation.constraints.Min.class), "pageSize: @Min");
    assertNotNull(size.getAnnotation(jakarta.validation.constraints.Max.class), "pageSize: @Max");
  }

  @Test
  void listReturnsSecondPageCorrectly() {
    List<DQRIO> all = List.of(
      makeDQRIO("dqr-1", "Rule A"),
      makeDQRIO("dqr-2", "Rule B"),
      makeDQRIO("dqr-3", "Rule C")
    );
    when(service.list(COLL_APP_ID, ALICE)).thenReturn(all);

    Response r = resource.list(COLL_APP_ID, 1, 2, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DQRIO> body = (List<DQRIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals("dqr-3", body.get(0).dqrAppId());
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private DQRIO makeDQRIO(String appId, String name) {
    return new DQRIO(appId, name, null, "ANNOTATION_REQUIRED", "label", "WARNING", true);
  }
}
