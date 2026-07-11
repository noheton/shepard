package de.dlr.shepard.v2.quality.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.quality.io.DQRIO;
import de.dlr.shepard.v2.quality.io.DQRResultIO;
import de.dlr.shepard.v2.quality.io.DQRResultsIO;
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
    verify(service, never()).list(anyString(), anyString(), anyLong(), anyInt());
  }

  @Test
  void listReturns200WithXTotalCount() {
    DQRIO dqr = makeDQRIO("dqr-1", "Must have name");
    when(service.list(COLL_APP_ID, ALICE, 0L, 50))
        .thenReturn(new PagedResponseIO<>(List.of(dqr), 1L, 0, 50));

    Response r = resource.list(COLL_APP_ID, 0, 50, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<DQRIO> body = (PagedResponseIO<DQRIO>) r.getEntity();
    assertEquals(1, body.items().size());
  }

  @Test
  void listPropagates403FromService() {
    when(service.list(eq(COLL_APP_ID), eq(ALICE), anyLong(), anyInt())).thenThrow(new ForbiddenException());
    org.junit.jupiter.api.Assertions.assertThrows(ForbiddenException.class,
      () -> resource.list(COLL_APP_ID, 0, 50, securityContext));
  }

  @Test
  void listDelegatesToServiceWithCorrectSkipAndLimit() {
    // page=0, pageSize=2 → service called with (COLL_APP_ID, ALICE, 0, 2)
    List<DQRIO> slice = List.of(makeDQRIO("dqr-1", "Rule A"), makeDQRIO("dqr-2", "Rule B"));
    when(service.list(COLL_APP_ID, ALICE, 0L, 2))
        .thenReturn(new PagedResponseIO<>(slice, 3L, 0, 2));

    Response r = resource.list(COLL_APP_ID, 0, 2, securityContext);

    assertEquals(200, r.getStatus());
    verify(service).list(COLL_APP_ID, ALICE, 0L, 2);
    @SuppressWarnings("unchecked")
    PagedResponseIO<DQRIO> body = (PagedResponseIO<DQRIO>) r.getEntity();
    assertEquals(2, body.items().size());
  }

  @Test
  void listComputesCorrectSkipForPageTwo() {
    // page=1, pageSize=2 → skip=2; service returns 1 remaining item
    when(service.list(COLL_APP_ID, ALICE, 2L, 2))
        .thenReturn(new PagedResponseIO<>(List.of(makeDQRIO("dqr-3", "Rule C")), 3L, 1, 2));

    Response r = resource.list(COLL_APP_ID, 1, 2, securityContext);

    assertEquals(200, r.getStatus());
    verify(service).list(COLL_APP_ID, ALICE, 2L, 2);
    @SuppressWarnings("unchecked")
    PagedResponseIO<DQRIO> body = (PagedResponseIO<DQRIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("dqr-3", body.items().get(0).dqrAppId());
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

  // ─── POST /dqr/evaluate (APISIMP-DQR-EVALUATE-BARE-LIST) ─────────────────

  @Test
  void evaluate_returns401_whenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.evaluate(COLL_APP_ID, 5000, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).evaluate(anyString(), anyString());
  }

  @Test
  void evaluate_returns200_notTruncated_whenResultsWithinLimit() {
    List<DQRResultIO> results = List.of(
      DQRResultIO.pass("dqr-1", "do-1"),
      DQRResultIO.fail("dqr-1", "do-2", "missing label")
    );
    when(service.evaluate(COLL_APP_ID, ALICE)).thenReturn(results);

    Response r = resource.evaluate(COLL_APP_ID, 5000, securityContext);

    assertEquals(200, r.getStatus());
    DQRResultsIO body = (DQRResultsIO) r.getEntity();
    assertEquals(2, body.results().size());
    assertEquals(false, body.truncated());
    assertEquals(2L, body.total());
  }

  @Test
  void evaluate_truncates_whenResultsExceedLimit() {
    List<DQRResultIO> results = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      results.add(DQRResultIO.pass("dqr-1", "do-" + i));
    }
    when(service.evaluate(COLL_APP_ID, ALICE)).thenReturn(results);

    Response r = resource.evaluate(COLL_APP_ID, 3, securityContext);

    assertEquals(200, r.getStatus());
    DQRResultsIO body = (DQRResultsIO) r.getEntity();
    assertEquals(3, body.results().size());
    assertEquals(true, body.truncated());
    assertEquals(10L, body.total());
  }

  @Test
  void evaluate_returns200_emptyEnvelope_whenNoDQRsEnabled() {
    when(service.evaluate(COLL_APP_ID, ALICE)).thenReturn(List.of());

    Response r = resource.evaluate(COLL_APP_ID, 5000, securityContext);

    assertEquals(200, r.getStatus());
    DQRResultsIO body = (DQRResultsIO) r.getEntity();
    assertEquals(0, body.results().size());
    assertEquals(false, body.truncated());
    assertEquals(0L, body.total());
  }

  @Test
  void evaluate_limitParamCarriesValidationAnnotations() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionDQRRest.class.getDeclaredMethod(
        "evaluate", String.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter limit = m.getParameters()[1];
    assertNotNull(limit.getAnnotation(jakarta.validation.constraints.Min.class), "limit: @Min");
    assertNotNull(limit.getAnnotation(jakarta.validation.constraints.Max.class), "limit: @Max");
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private DQRIO makeDQRIO(String appId, String name) {
    return new DQRIO(appId, name, null, "ANNOTATION_REQUIRED", "label", "WARNING", true);
  }
}
