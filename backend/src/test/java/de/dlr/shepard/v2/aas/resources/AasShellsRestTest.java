package de.dlr.shepard.v2.aas.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import de.dlr.shepard.aas.services.AasShellMappingService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.v2.aas.io.AasShellIO;
import de.dlr.shepard.v2.aas.io.AasShellIO.AssetInformationIO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for AAS1a: {@link AasShellsRest}. */
class AasShellsRestTest {

  @Mock
  CollectionDAO collectionDAO;

  @Mock
  AasShellMappingService mappingService;

  @Mock
  AuthenticationContext authenticationContext;

  AasShellsRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new AasShellsRest();
    resource.collectionDAO = collectionDAO;
    resource.mappingService = mappingService;
    resource.authenticationContext = authenticationContext;
    when(authenticationContext.getCurrentUserName()).thenReturn("alice");
  }

  private Collection makeCollection(String appId, String name) {
    Collection c = new Collection(1L);
    c.setAppId(appId);
    c.setName(name);
    return c;
  }

  private AasShellIO makeShell(String appId) {
    return new AasShellIO(
        "urn:shepard:collection:" + appId,
        "name",
        new AssetInformationIO("Instance", "urn:shepard:asset:" + appId),
        null,
        List.of());
  }

  @Test
  void returnsEmptyListWhenNoCollections() {
    when(collectionDAO.findAllCollectionsByShepardId(any(), any())).thenReturn(List.of());
    var r = resource.listShells(null, 100);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (List<AasShellIO>) r.getEntity();
    assertEquals(0, body.size());
  }

  @Test
  void mapsEachCollectionToShell() {
    Collection c1 = makeCollection("id-1", "Alpha");
    Collection c2 = makeCollection("id-2", "Beta");
    AasShellIO s1 = makeShell("id-1");
    AasShellIO s2 = makeShell("id-2");
    when(collectionDAO.findAllCollectionsByShepardId(any(), any())).thenReturn(List.of(c1, c2));
    when(mappingService.toShell(c1)).thenReturn(s1);
    when(mappingService.toShell(c2)).thenReturn(s2);

    var r = resource.listShells(null, 100);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var shells = (List<AasShellIO>) r.getEntity();
    assertEquals(2, shells.size());
    assertEquals("urn:shepard:collection:id-1", shells.get(0).getId());
    assertEquals("urn:shepard:collection:id-2", shells.get(1).getId());
  }

  @Test
  void passesCallerUsernameToDAO() {
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(collectionDAO.findAllCollectionsByShepardId(any(), any())).thenReturn(List.of());
    resource.listShells(null, 100);
    // verify username was propagated — mockito would have matched `any()` regardless,
    // so we capture via the verify below
    org.mockito.Mockito.verify(collectionDAO)
        .findAllCollectionsByShepardId(any(), org.mockito.Mockito.eq("bob"));
  }

  @Test
  void anonymousUserPassesNullUsername() {
    when(authenticationContext.getCurrentUserName()).thenReturn(null);
    when(collectionDAO.findAllCollectionsByShepardId(any(), isNull())).thenReturn(List.of());
    var r = resource.listShells(null, 100);
    assertEquals(200, r.getStatus());
    org.mockito.Mockito.verify(collectionDAO)
        .findAllCollectionsByShepardId(any(), isNull());
  }

  @Test
  void returns200WithSingleShell() {
    Collection c = makeCollection("solo", "Solo");
    AasShellIO shell = makeShell("solo");
    when(collectionDAO.findAllCollectionsByShepardId(any(), any())).thenReturn(List.of(c));
    when(mappingService.toShell(c)).thenReturn(shell);

    var r = resource.listShells(null, 100);
    assertEquals(200, r.getStatus());
  }
}
