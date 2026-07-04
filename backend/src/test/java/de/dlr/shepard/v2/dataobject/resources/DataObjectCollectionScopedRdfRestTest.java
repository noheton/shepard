package de.dlr.shepard.v2.dataobject.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-DO-ROOT-PATH-INCONSISTENCY — verifies that the collection-scoped alias
 * {@code GET /v2/collections/{collectionAppId}/data-objects/{appId}/rdf}
 * produces the same HTTP status as the flat path for the three gate cases
 * (200 / 403 / 404).
 */
class DataObjectCollectionScopedRdfRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-0000000000ee";
  static final String DO_APP_ID   = "018f9c5a-7e26-7000-a000-0000000000aa";
  static final String CALLER      = "alice";

  @Mock DataObjectDAO dataObjectDAO;
  @Mock SemanticAnnotationDAO semanticAnnotationDAO;
  @Mock PermissionsService permissionsService;
  @Mock SecurityContext securityContext;
  @Mock Principal principal;

  DataObjectCollectionScopedRdfRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    // Wire up the flat-path delegate with mocked dependencies.
    DataObjectRdfRest flat = new DataObjectRdfRest();
    flat.dataObjectDAO = dataObjectDAO;
    flat.semanticAnnotationDAO = semanticAnnotationDAO;
    flat.permissionsService = permissionsService;

    resource = new DataObjectCollectionScopedRdfRest();
    resource.delegate = flat;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(semanticAnnotationDAO.findBySubjectAppId(any())).thenReturn(List.of());
  }

  @Test
  void collectionScopedAlias_returnsIdenticalStatus_200() {
    DataObject d = new DataObject();
    d.setAppId(DO_APP_ID);
    d.setName("TR-004");
    d.setCreatedAt(new Date(1_700_000_000_000L));
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(d);
    when(permissionsService.isAccessAllowedForDataObjectAppId(
        eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);

    Response r = resource.getRdf(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
  }

  @Test
  void collectionScopedAlias_returnsIdenticalStatus_404() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(null);

    Response r = resource.getRdf(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(404, r.getStatus());
  }

  @Test
  void collectionScopedAlias_returnsIdenticalStatus_403() {
    DataObject d = new DataObject();
    d.setAppId(DO_APP_ID);
    d.setName("TR-004");
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(d);
    when(permissionsService.isAccessAllowedForDataObjectAppId(
        eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);

    Response r = resource.getRdf(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(403, r.getStatus());
  }
}
