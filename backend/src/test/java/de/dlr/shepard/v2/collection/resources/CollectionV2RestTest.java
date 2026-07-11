package de.dlr.shepard.v2.collection.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.v2.collection.io.CollectionV2IO;
import de.dlr.shepard.v2.collection.io.CreateCollectionV2IO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.validation.Validator;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * L2d Phase A — unit tests for the {@code /v2/collections} resource.
 *
 * <p>Mock-based, no Quarkus boot. Covers the same six-shape grid the
 * snapshot tests use: 401 when unauthenticated, 404 when the appId
 * doesn't resolve, 403 when the permission check fails, and the
 * happy-path 200 / 201 / 204 outcomes.
 */
class CollectionV2RestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000010";
  static final long COLL_OGM_ID = 42L;
  static final String CALLER = "alice";

  @Mock
  CollectionService collectionService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  Validator validator;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  CollectionV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionV2Rest();
    resource.collectionService = collectionService;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    resource.validator = validator;
    resource.objectMapper = new ObjectMapper();
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(validator.validate(any())).thenReturn(Collections.emptySet());
  }

  // ── list ──────────────────────────────────────────────────────────────────

  @Test
  void listReturns200WithPagedEnvelope() {
    Collection c = new Collection();
    c.setShepardId(COLL_OGM_ID);
    c.setAppId(COLL_APP_ID);
    c.setName("demo");
    when(collectionService.countAllCollections(any())).thenReturn(1L);
    when(collectionService.getAllCollections(any())).thenReturn(List.of(c));

    Response r = resource.list(null, null, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<CollectionV2IO> body = (PagedResponseIO<CollectionV2IO>) r.getEntity();
    assertEquals(1, body.total());
    assertEquals(0, body.page());
    assertEquals(50, body.pageSize());
    assertEquals(1, body.items().size());
    assertEquals(COLL_APP_ID, body.items().get(0).getAppId());
    // X-Total-Count header kept during deprecation window
  }

  @Test
  void listReturnsEmptyPagedEnvelopeWhenNoCollections() {
    when(collectionService.countAllCollections(any())).thenReturn(0L);
    when(collectionService.getAllCollections(any())).thenReturn(List.of());

    Response r = resource.list(null, null, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<CollectionV2IO> body = (PagedResponseIO<CollectionV2IO>) r.getEntity();
    assertEquals(0, body.total());
    assertEquals(0, body.items().size());
  }

  @Test
  void list_qParam_noDeprecationHeader() {
    when(collectionService.countAllCollections(any())).thenReturn(0L);
    when(collectionService.getAllCollections(any())).thenReturn(List.of());

    Response r = resource.list("search-term", null, 0, 50);

    assertEquals(200, r.getStatus());
    // ?q= is the preferred param; no Deprecation header must be emitted
    assertNull(r.getHeaderString("Deprecation"),
        "Deprecation header must NOT be present when using preferred ?q= param");
  }

  @Test
  void list_legacyNameParam_emitsDeprecationHeader() {
    when(collectionService.countAllCollections(any())).thenReturn(0L);
    when(collectionService.getAllCollections(any())).thenReturn(List.of());

    Response r = resource.list(null, "legacy-name", 0, 50);

    assertEquals(200, r.getStatus());
    assertEquals("true", r.getHeaderString("Deprecation"),
        "Deprecation: true must be emitted when caller uses deprecated ?name= param");
  }

  @Test
  void list_qParamWinsOverLegacyName() {
    when(collectionService.countAllCollections(any())).thenReturn(0L);
    when(collectionService.getAllCollections(any())).thenReturn(List.of());

    // When both are supplied ?q= wins and Deprecation header is NOT emitted
    Response r = resource.list("preferred", "legacy", 0, 50);

    assertEquals(200, r.getStatus());
    assertNull(r.getHeaderString("Deprecation"),
        "Deprecation header must NOT be present when ?q= overrides ?name=");
  }

  @Test
  void list_pageSizeParam_hasMinConstraint() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionV2Rest.class.getMethod("list", String.class, String.class, int.class, int.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "pageSize".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "list.pageSize must carry @QueryParam");
    assertNotNull(param.getAnnotation(jakarta.validation.constraints.Min.class), "pageSize must have @Min constraint");
    assertEquals(1L, param.getAnnotation(jakarta.validation.constraints.Min.class).value(), "pageSize @Min must be 1");
  }

  @Test
  void list_pageSizeParam_hasMaxConstraint() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionV2Rest.class.getMethod("list", String.class, String.class, int.class, int.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "pageSize".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "list.pageSize must carry @QueryParam");
    assertNotNull(param.getAnnotation(jakarta.validation.constraints.Max.class), "pageSize must have @Max constraint");
    assertEquals(200L, param.getAnnotation(jakarta.validation.constraints.Max.class).value(), "pageSize @Max must be 200");
  }

  // ── get ────────────────────────────────────────────────────────────────────

  @Test
  void getReturns404WhenAppIdUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), any(), any(), anyLong());
  }

  @Test
  void getReturns401WhenUnauthenticated() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void getReturns403WhenNoReadPermission() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(collectionService, never()).getCollectionWithDataObjectsAndIncomingReferences(anyLong());
  }

  @Test
  void getReturns200WithCollection() {
    Collection c = new Collection();
    c.setShepardId(COLL_OGM_ID);
    c.setAppId(COLL_APP_ID);
    c.setName("demo");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(c);

    Response r = resource.get(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    assertNotNull(io);
    assertEquals(COLL_APP_ID, io.getAppId());
    assertEquals("demo", io.getName());
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void createReturns201WithMintedAppId() {
    CreateCollectionV2IO body = new CreateCollectionV2IO();
    body.setName("new collection");

    Collection created = new Collection();
    created.setShepardId(99L);
    created.setAppId("018f9c5a-9999-7000-a000-000000000099");
    created.setName("new collection");
    when(collectionService.createCollection(any())).thenReturn(created);

    Response r = resource.create(body);

    assertEquals(201, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    assertEquals("018f9c5a-9999-7000-a000-000000000099", io.getAppId());
  }

  @Test
  void createWithDefaultFileContainerAppId_translatesFieldToCollectionIO() {
    CreateCollectionV2IO body = new CreateCollectionV2IO();
    body.setName("with default fc");
    body.setDefaultFileContainerAppId("018f9c5a-fc00-7000-a000-000000000001");

    Collection created = new Collection();
    created.setShepardId(55L);
    created.setAppId("018f9c5a-5555-7000-a000-000000000055");
    created.setName("with default fc");

    // Capture the CollectionIO that the service receives to verify appId translation.
    org.mockito.ArgumentCaptor<CollectionIO> captor = org.mockito.ArgumentCaptor.forClass(CollectionIO.class);
    when(collectionService.createCollection(captor.capture())).thenReturn(created);

    Response r = resource.create(body);

    assertEquals(201, r.getStatus());
    assertEquals("018f9c5a-fc00-7000-a000-000000000001",
      captor.getValue().getDefaultFileContainerAppId(),
      "defaultFileContainerAppId must be translated into the CollectionIO passed to the service");
    assertEquals(null, captor.getValue().getDefaultFileContainerId(),
      "defaultFileContainerId must NOT be set (v2 input does not carry the legacy Long)");
  }

  // ── patch ─────────────────────────────────────────────────────────────────

  @Test
  void patchReturns400WhenBodyIsNotAnObject() {
    // RFC 7396 requires the body to be a JSON object.
    assertThrows(InvalidBodyException.class, () ->
      resource.patch(COLL_APP_ID, JsonNodeFactory.instance.arrayNode(), securityContext)
    );
  }

  @Test
  void patchReturns404WhenAppIdUnknown() {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "new desc");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());

    Response r = resource.patch(COLL_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void patchReturns403WhenNoWritePermission() {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "new desc");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);

    Response r = resource.patch(COLL_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(collectionService, never()).updateCollectionByShepardId(anyLong(), any());
  }

  @Test
  void patchReturns200WithMergedBody() {
    Collection existing = new Collection();
    existing.setShepardId(COLL_OGM_ID);
    existing.setAppId(COLL_APP_ID);
    existing.setName("demo");
    existing.setDescription("old description");

    Collection updated = new Collection();
    updated.setShepardId(COLL_OGM_ID);
    updated.setAppId(COLL_APP_ID);
    updated.setName("demo");
    updated.setDescription("new description");

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "new description");

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(existing);
    when(collectionService.updateCollectionByShepardId(eq(COLL_OGM_ID), any())).thenReturn(updated);

    Response r = resource.patch(COLL_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    assertEquals("new description", io.getDescription());
  }

  // ── promptLogMode (PROMPT-h2) ─────────────────────────────────────────────

  @Test
  void createWithPromptLogModePersistsIt() {
    CreateCollectionV2IO body = new CreateCollectionV2IO();
    body.setName("with prompt log mode");
    body.setPromptLogMode("BODY_RAW");

    Collection created = new Collection();
    created.setShepardId(88L);
    created.setAppId("018f9c5a-8888-7000-a000-000000000088");
    created.setName("with prompt log mode");
    created.setPromptLogMode("BODY_RAW");
    when(collectionService.createCollection(any())).thenReturn(created);

    Response r = resource.create(body);

    assertEquals(201, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    assertEquals("BODY_RAW", io.getPromptLogMode());
  }

  @Test
  void patchWithPromptLogModeUpdatesIt() {
    Collection existing = new Collection();
    existing.setShepardId(COLL_OGM_ID);
    existing.setAppId(COLL_APP_ID);
    existing.setName("demo");
    existing.setPromptLogMode("HASH_ONLY");

    Collection updated = new Collection();
    updated.setShepardId(COLL_OGM_ID);
    updated.setAppId(COLL_APP_ID);
    updated.setName("demo");
    updated.setPromptLogMode("BODY_REDACTED");

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("promptLogMode", "BODY_REDACTED");

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(existing);
    when(collectionService.updateCollectionByShepardId(eq(COLL_OGM_ID), any())).thenReturn(updated);

    Response r = resource.patch(COLL_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    assertEquals("BODY_REDACTED", io.getPromptLogMode());
  }

  @Test
  void patchWithoutPromptLogModeLeavesItUnchanged() {
    Collection existing = new Collection();
    existing.setShepardId(COLL_OGM_ID);
    existing.setAppId(COLL_APP_ID);
    existing.setName("demo");
    existing.setPromptLogMode("BODY_RAW");

    Collection afterUpdate = new Collection();
    afterUpdate.setShepardId(COLL_OGM_ID);
    afterUpdate.setAppId(COLL_APP_ID);
    afterUpdate.setName("demo");
    afterUpdate.setDescription("new desc");
    afterUpdate.setPromptLogMode("BODY_RAW");

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "new desc");

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(existing);
    when(collectionService.updateCollectionByShepardId(eq(COLL_OGM_ID), any())).thenReturn(afterUpdate);

    Response r = resource.patch(COLL_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    assertEquals("BODY_RAW", io.getPromptLogMode());
  }

  // ── heroImageUrl (Feature B) ───────────────────────────────────────────────

  @Test
  void createWithHeroImageUrlPersistsIt() {
    CreateCollectionV2IO body = new CreateCollectionV2IO();
    body.setName("with hero");
    body.setHeroImageUrl("https://example.com/banner.jpg");

    Collection created = new Collection();
    created.setShepardId(77L);
    created.setAppId("018f9c5a-7777-7000-a000-000000000077");
    created.setName("with hero");
    created.setHeroImageUrl("https://example.com/banner.jpg");
    when(collectionService.createCollection(any())).thenReturn(created);

    Response r = resource.create(body);

    assertEquals(201, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    assertEquals("https://example.com/banner.jpg", io.getHeroImageUrl());
  }

  @Test
  void patchWithNullHeroImageUrlClearsIt() {
    Collection existing = new Collection();
    existing.setShepardId(COLL_OGM_ID);
    existing.setAppId(COLL_APP_ID);
    existing.setName("demo");
    existing.setHeroImageUrl("https://example.com/old-banner.jpg");

    Collection updated = new Collection();
    updated.setShepardId(COLL_OGM_ID);
    updated.setAppId(COLL_APP_ID);
    updated.setName("demo");
    updated.setHeroImageUrl(null);

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.putNull("heroImageUrl");

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(existing);
    when(collectionService.updateCollectionByShepardId(eq(COLL_OGM_ID), any())).thenReturn(updated);

    Response r = resource.patch(COLL_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    assertNotNull(io);
    // Returned IO mirrors the updated entity — heroImageUrl is null (cleared).
    assertEquals(null, io.getHeroImageUrl());
  }

  @Test
  void patchWithoutHeroImageUrlLeavesItUnchanged() {
    String originalUrl = "https://example.com/banner.jpg";

    Collection existing = new Collection();
    existing.setShepardId(COLL_OGM_ID);
    existing.setAppId(COLL_APP_ID);
    existing.setName("demo");
    existing.setHeroImageUrl(originalUrl);

    // Patch body only touches description — heroImageUrl is absent.
    // RFC 7396: absent fields are left unchanged; Jackson's readerForUpdating
    // preserves the field value from the existing CollectionIO.
    Collection afterUpdate = new Collection();
    afterUpdate.setShepardId(COLL_OGM_ID);
    afterUpdate.setAppId(COLL_APP_ID);
    afterUpdate.setName("demo");
    afterUpdate.setDescription("new desc");
    afterUpdate.setHeroImageUrl(originalUrl);

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "new desc");

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(existing);
    when(collectionService.updateCollectionByShepardId(eq(COLL_OGM_ID), any())).thenReturn(afterUpdate);

    Response r = resource.patch(COLL_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    CollectionIO io = (CollectionIO) r.getEntity();
    // The returned IO (mapped from afterUpdate) still carries the original URL.
    assertEquals(originalUrl, io.getHeroImageUrl());
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  void deleteReturns404WhenAppIdUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.delete(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(collectionService, never()).deleteCollection(anyLong());
  }

  @Test
  void deleteReturns403WhenNoWritePermission() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.delete(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(collectionService, never()).deleteCollection(anyLong());
  }

  @Test
  void deleteReturns204OnSuccess() {
    Collection c = mock(Collection.class);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);

    Response r = resource.delete(COLL_APP_ID, securityContext);

    assertEquals(204, r.getStatus());
    verify(collectionService).deleteCollection(COLL_OGM_ID);
  }

  // ── route-pinning ─────────────────────────────────────────────────────────
  //
  // BUG-V2-COLL-LIST-404 (2026-05-30 UI scrutinizer): audit reported
  // `/v2/collections` returning 404. Investigation showed the audit
  // curl'd `https://shepard.nuclide.systems/shepard/api/v2/collections`
  // — the `/shepard/api/` prefix is the upstream-frozen v1 base path
  // and does NOT apply to `/v2/...` resources (quarkus.http.root-path=/
  // per application.properties). Direct `curl /v2/collections` returns
  // 401 (auth required, endpoint present). False positive — but pin
  // the path literally here so any future class-level @Path rename
  // surfaces as a test failure rather than a silent 404.

  @Test
  void classLevelPathIsExactlyV2Collections() {
    jakarta.ws.rs.Path classPath = CollectionV2Rest.class.getAnnotation(jakarta.ws.rs.Path.class);
    assertNotNull(classPath, "CollectionV2Rest must carry a @Path annotation");
    assertEquals("/v2/collections", classPath.value(),
      "Class-level @Path is the wire contract; any change breaks every /v2/collections caller");
  }

  @Test
  void getMethodPathIsRelativeAppIdParam() throws NoSuchMethodException {
    jakarta.ws.rs.Path methodPath = CollectionV2Rest.class
      .getMethod("get", String.class, SecurityContext.class)
      .getAnnotation(jakarta.ws.rs.Path.class);
    assertNotNull(methodPath, "get(...) must carry an @Path annotation");
    assertEquals("/{appId}", methodPath.value(),
      "get(...) path is the wire contract for GET /v2/collections/{appId}");
  }

  // ─── APISIMP-REMAINING-PARAMS reflection guards ────────────────────────────

  @Test
  void list_qParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionV2Rest.class.getMethod("list", String.class, String.class, int.class, int.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "q".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "list.q must carry @QueryParam(\"q\")");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "list.q must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for list.q");
  }

  @Test
  void list_legacyNameParam_isDeprecated() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionV2Rest.class.getMethod("list", String.class, String.class, int.class, int.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "name".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "list.name (legacy) must still carry @QueryParam(\"name\")");
    assertNotNull(param.getAnnotation(Deprecated.class), "list.name must be annotated @Deprecated");
  }

  // ─── APISIMP-COLLECTION-LIST-BUNDLE-SIZE-PAGESIZE reflection guards ────────

  @Test
  void list_pageParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionV2Rest.class.getMethod("list", String.class, String.class, int.class, int.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "page".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "list.page must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "list.page must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for list.page");
  }

  @Test
  void list_pageSizeParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionV2Rest.class.getMethod("list", String.class, String.class, int.class, int.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "pageSize".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "list.pageSize must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "list.pageSize must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for list.pageSize");
  }
}
