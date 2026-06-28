package de.dlr.shepard.plugins.aas.v2.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import de.dlr.shepard.plugins.aas.services.AasConfigService;
import de.dlr.shepard.plugins.aas.services.AasShellMappingService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.plugins.aas.v2.io.AasReferenceIO;
import de.dlr.shepard.plugins.aas.v2.io.AasReferenceIO.AasKeyIO;
import de.dlr.shepard.plugins.aas.v2.io.AasShellIO;
import de.dlr.shepard.plugins.aas.v2.io.AasShellIO.AssetInformationIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
  DataObjectDAO dataObjectDAO;

  @Mock
  AasShellMappingService mappingService;

  @Mock
  AuthenticationContext authenticationContext;

  @Mock
  AasConfigService aasConfigService;

  AasShellsRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new AasShellsRest();
    resource.collectionDAO = collectionDAO;
    resource.dataObjectDAO = dataObjectDAO;
    resource.mappingService = mappingService;
    resource.authenticationContext = authenticationContext;
    resource.aasConfigService = aasConfigService;
    AasConfig enabledConfig = new AasConfig();
    enabledConfig.setEnabled(true);
    when(aasConfigService.current()).thenReturn(enabledConfig);
    when(authenticationContext.getCurrentUserName()).thenReturn("alice");
    when(collectionDAO.countAllCollectionsByShepardId(any())).thenReturn(0L);
    when(dataObjectDAO.findTopLevelByCollectionAppId(any())).thenReturn(List.of());
    when(dataObjectDAO.findTopLevelByCollectionAppId(any(), anyInt(), anyInt())).thenReturn(List.of());
    when(dataObjectDAO.countTopLevelByCollectionAppId(any())).thenReturn(0L);
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
    when(collectionDAO.countAllCollectionsByShepardId(any())).thenReturn(0L);
    var r = resource.listShells(0, 50);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<AasShellIO>) r.getEntity();
    assertEquals(0, body.items().size());
    assertEquals(0L, body.total());
    assertEquals(0, body.page());
    assertEquals(50, body.pageSize());
  }

  @Test
  void mapsEachCollectionToShell() {
    Collection c1 = makeCollection("id-1", "Alpha");
    Collection c2 = makeCollection("id-2", "Beta");
    AasShellIO s1 = makeShell("id-1");
    AasShellIO s2 = makeShell("id-2");
    when(collectionDAO.findAllCollectionsByShepardId(any(), any())).thenReturn(List.of(c1, c2));
    when(collectionDAO.countAllCollectionsByShepardId(any())).thenReturn(2L);
    when(mappingService.toShell(c1)).thenReturn(s1);
    when(mappingService.toShell(c2)).thenReturn(s2);

    var r = resource.listShells(0, 50);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<AasShellIO>) r.getEntity();
    assertEquals(2, body.items().size());
    assertEquals(2L, body.total());
    assertEquals("urn:shepard:collection:id-1", body.items().get(0).getId());
    assertEquals("urn:shepard:collection:id-2", body.items().get(1).getId());
  }

  @Test
  void passesCallerUsernameToDAO() {
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(collectionDAO.findAllCollectionsByShepardId(any(), any())).thenReturn(List.of());
    when(collectionDAO.countAllCollectionsByShepardId(eq("bob"))).thenReturn(0L);
    resource.listShells(0, 50);
    org.mockito.Mockito.verify(collectionDAO)
        .findAllCollectionsByShepardId(any(), org.mockito.Mockito.eq("bob"));
    org.mockito.Mockito.verify(collectionDAO)
        .countAllCollectionsByShepardId(org.mockito.Mockito.eq("bob"));
  }

  @Test
  void anonymousUserPassesNullUsername() {
    when(authenticationContext.getCurrentUserName()).thenReturn(null);
    when(collectionDAO.findAllCollectionsByShepardId(any(), isNull())).thenReturn(List.of());
    when(collectionDAO.countAllCollectionsByShepardId(isNull())).thenReturn(0L);
    var r = resource.listShells(0, 50);
    assertEquals(200, r.getStatus());
    org.mockito.Mockito.verify(collectionDAO)
        .findAllCollectionsByShepardId(any(), isNull());
  }

  @Test
  void returns200WithSingleShell() {
    Collection c = makeCollection("solo", "Solo");
    AasShellIO shell = makeShell("solo");
    when(collectionDAO.findAllCollectionsByShepardId(any(), any())).thenReturn(List.of(c));
    when(collectionDAO.countAllCollectionsByShepardId(any())).thenReturn(1L);
    when(mappingService.toShell(c)).thenReturn(shell);

    var r = resource.listShells(0, 50);
    assertEquals(200, r.getStatus());
  }

  @Test
  void pageSizeIsCappedAt200() {
    when(collectionDAO.findAllCollectionsByShepardId(any(), any())).thenReturn(List.of());
    when(collectionDAO.countAllCollectionsByShepardId(any())).thenReturn(0L);
    var r = resource.listShells(0, 9999);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<AasShellIO>) r.getEntity();
    assertEquals(200, body.pageSize());
  }

  // --- resolveAppId (AAS1b Commit 1) ---

  @Test
  void resolveAppIdPassesThroughRawAppId() {
    assertEquals("col-aaa-111", AasShellsRest.resolveAppId("col-aaa-111"));
  }

  @Test
  void resolveAppIdDecodesBase64UrlIri() {
    String iri = "urn:shepard:collection:col-aaa-111";
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(iri.getBytes(StandardCharsets.UTF_8));
    assertEquals("col-aaa-111", AasShellsRest.resolveAppId(encoded));
  }

  @Test
  void resolveAppIdDecodesBase64UrlIriWithPadding() {
    String iri = "urn:shepard:collection:col-aaa-111";
    String encoded = Base64.getUrlEncoder().encodeToString(iri.getBytes(StandardCharsets.UTF_8));
    assertEquals("col-aaa-111", AasShellsRest.resolveAppId(encoded));
  }

  @Test
  void resolveAppIdFallsBackForInvalidBase64() {
    // "col-aaa-111" is valid base64url but decodes to bytes that do not start with the collection prefix
    String raw = "col-aaa-111";
    // Result must be the raw value (no exception, no prefix match)
    assertEquals(raw, AasShellsRest.resolveAppId(raw));
  }

  @Test
  void resolveAppIdFallsBackForBase64ThatDecodesToNonCollectionIri() {
    // base64url-encode a non-collection IRI
    String otherIri = "urn:other:prefix:abc";
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(otherIri.getBytes(StandardCharsets.UTF_8));
    // decodes fine but prefix doesn't match → return raw encoded string as appId
    assertEquals(encoded, AasShellsRest.resolveAppId(encoded));
  }

  // --- GET /v2/aas/shells/{aasId} (AAS1b Commit 1) ---

  @Test
  void getShellReturns200ForRawAppId() {
    Collection c = makeCollection("col-aaa-111", "Alpha");
    AasShellIO shell = makeShell("col-aaa-111");
    when(collectionDAO.findByAppId(eq("col-aaa-111"), any())).thenReturn(c);
    when(mappingService.toShell(eq(c), eq(List.of()))).thenReturn(shell);

    var r = resource.getShell("col-aaa-111");

    assertEquals(200, r.getStatus());
    assertEquals(shell, r.getEntity());
  }

  @Test
  void getShellReturns200ForBase64UrlEncodedIri() {
    String iri = "urn:shepard:collection:col-aaa-111";
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(iri.getBytes(StandardCharsets.UTF_8));
    Collection c = makeCollection("col-aaa-111", "Alpha");
    AasShellIO shell = makeShell("col-aaa-111");
    when(collectionDAO.findByAppId(eq("col-aaa-111"), any())).thenReturn(c);
    when(mappingService.toShell(eq(c), eq(List.of()))).thenReturn(shell);

    var r = resource.getShell(encoded);

    assertEquals(200, r.getStatus());
    assertEquals(shell, r.getEntity());
  }

  @Test
  void getShellReturns404WhenNotFound() {
    when(collectionDAO.findByAppId(any(), any())).thenReturn(null);

    var r = resource.getShell("no-such-id");

    assertEquals(404, r.getStatus());
  }

  @Test
  void getShellPassesUsernameToDAO() {
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(collectionDAO.findByAppId(any(), any())).thenReturn(null);

    resource.getShell("col-aaa-111");

    verify(collectionDAO).findByAppId(eq("col-aaa-111"), eq("bob"));
  }

  @Test
  void getShellPopulatesSubmodelsFromDataObjects() {
    Collection c = makeCollection("col-aaa-111", "Alpha");
    DataObject d = org.mockito.Mockito.mock(DataObject.class);
    org.mockito.Mockito.when(d.getAppId()).thenReturn("do-111");
    AasReferenceIO ref = new AasReferenceIO("ExternalReference",
        List.of(new AasKeyIO("Submodel", "urn:shepard:dataobject:do-111")));
    AasShellIO shellWithSubmodels = new AasShellIO(
        "urn:shepard:collection:col-aaa-111", "Alpha",
        new AssetInformationIO("Instance", "urn:shepard:asset:col-aaa-111"),
        null, List.of(ref));
    when(collectionDAO.findByAppId(eq("col-aaa-111"), any())).thenReturn(c);
    when(dataObjectDAO.findTopLevelByCollectionAppId(eq("col-aaa-111"))).thenReturn(List.of(d));
    when(mappingService.toShell(eq(c), eq(List.of(d)))).thenReturn(shellWithSubmodels);

    var r = resource.getShell("col-aaa-111");

    assertEquals(200, r.getStatus());
    AasShellIO body = (AasShellIO) r.getEntity();
    assertEquals(1, body.getSubmodels().size());
    assertEquals("urn:shepard:dataobject:do-111", body.getSubmodels().get(0).keys().get(0).value());
  }

  // --- GET /v2/aas/shells/{aasId}/submodels (AAS1b Commit 2) ---

  @Test
  void listSubmodelsReturns404WhenShellNotFound() {
    when(collectionDAO.findByAppId(any(), any())).thenReturn(null);

    var r = resource.listSubmodels("no-such-id", 0, 50);

    assertEquals(404, r.getStatus());
  }

  @Test
  void listSubmodelsReturnsEmptyListForCollectionWithNoDataObjects() {
    Collection c = makeCollection("col-aaa-111", "Alpha");
    when(collectionDAO.findByAppId(eq("col-aaa-111"), any())).thenReturn(c);
    when(dataObjectDAO.findTopLevelByCollectionAppId(eq("col-aaa-111"), anyInt(), anyInt())).thenReturn(List.of());
    when(dataObjectDAO.countTopLevelByCollectionAppId(eq("col-aaa-111"))).thenReturn(0L);
    when(mappingService.toSubmodelRefs(List.of())).thenReturn(List.of());

    var r = resource.listSubmodels("col-aaa-111", 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<AasReferenceIO>) r.getEntity();
    assertEquals(0, body.items().size());
    assertEquals(0L, body.total());
  }

  @Test
  void listSubmodelsReturnsMappedReferences() {
    Collection c = makeCollection("col-aaa-111", "Alpha");
    DataObject d = org.mockito.Mockito.mock(DataObject.class);
    AasReferenceIO ref = new AasReferenceIO("ExternalReference",
        List.of(new AasKeyIO("Submodel", "urn:shepard:dataobject:do-111")));
    when(collectionDAO.findByAppId(eq("col-aaa-111"), any())).thenReturn(c);
    when(dataObjectDAO.findTopLevelByCollectionAppId(eq("col-aaa-111"), anyInt(), anyInt())).thenReturn(List.of(d));
    when(dataObjectDAO.countTopLevelByCollectionAppId(eq("col-aaa-111"))).thenReturn(1L);
    when(mappingService.toSubmodelRefs(List.of(d))).thenReturn(List.of(ref));

    var r = resource.listSubmodels("col-aaa-111", 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<AasReferenceIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals("ExternalReference", body.items().get(0).type());
  }

  @Test
  void listSubmodelsPassesUsernameToDAO() {
    when(authenticationContext.getCurrentUserName()).thenReturn("carol");
    when(collectionDAO.findByAppId(any(), any())).thenReturn(null);

    resource.listSubmodels("col-aaa-111", 0, 50);

    verify(collectionDAO).findByAppId(eq("col-aaa-111"), eq("carol"));
  }

  @Test
  void listSubmodelsEnvelopeContainsPageMetadata() {
    Collection c = makeCollection("col-aaa-111", "Alpha");
    when(collectionDAO.findByAppId(eq("col-aaa-111"), any())).thenReturn(c);
    when(dataObjectDAO.findTopLevelByCollectionAppId(eq("col-aaa-111"), anyInt(), anyInt())).thenReturn(List.of());
    when(dataObjectDAO.countTopLevelByCollectionAppId(eq("col-aaa-111"))).thenReturn(5L);
    when(mappingService.toSubmodelRefs(any())).thenReturn(List.of());

    var r = resource.listSubmodels("col-aaa-111", 1, 2);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<AasReferenceIO>) r.getEntity();
    assertEquals(5L, body.total());
    assertEquals(1, body.page());
    assertEquals(2, body.pageSize());
  }

  // --- aasDisabledResponse (APISIMP-AAS-SHELLS-DISABLED-ENVELOPE) ---

  @Test
  void returns501ProblemJsonWhenAasDisabled() {
    AasConfig disabledConfig = new AasConfig();
    disabledConfig.setEnabled(false);
    when(aasConfigService.current()).thenReturn(disabledConfig);

    var r = resource.listShells(0, 50);

    assertEquals(501, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals("/problems/aas.integration-disabled", body.type());
    assertEquals("AAS Integration Disabled", body.title());
    assertEquals(501, body.status());
  }
}
