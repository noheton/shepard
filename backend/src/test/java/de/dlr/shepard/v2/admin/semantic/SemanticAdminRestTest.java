package de.dlr.shepard.v2.admin.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.OntologyRefreshService;
import de.dlr.shepard.context.semantic.OntologyRefreshService.BundleError;
import de.dlr.shepard.context.semantic.OntologyRefreshService.RefreshOutcome;
import de.dlr.shepard.context.semantic.OntologySeedService;
import de.dlr.shepard.context.semantic.OntologySeedService.OntologyEntry;
import de.dlr.shepard.context.semantic.entities.UserOntologyBundle;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.BundleView;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.RemoveResult;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.SetEnabledResult;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.UploadResult;
import de.dlr.shepard.v2.admin.semantic.io.OntologyBundleIO;
import de.dlr.shepard.v2.admin.semantic.io.OntologyBundleListIO;
import de.dlr.shepard.v2.admin.semantic.io.RefreshOntologiesRequestIO;
import de.dlr.shepard.v2.admin.semantic.io.RefreshOntologiesResultIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticAdminRestTest {

  private OntologyRefreshService refreshService;
  private OntologyConfigService configService;
  private OntologySeedService seedService;
  private AuthenticationContext authCtx;
  private SecurityContext securityContext;

  @TempDir
  java.nio.file.Path tempDir;

  private SemanticAdminRest rest;

  @BeforeEach
  void setUp() {
    refreshService = mock(OntologyRefreshService.class);
    configService = mock(OntologyConfigService.class);
    seedService = mock(OntologySeedService.class);
    authCtx = mock(AuthenticationContext.class);
    securityContext = mock(SecurityContext.class);
    when(securityContext.getUserPrincipal()).thenReturn(() -> "admin");
    when(securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);

    rest = new SemanticAdminRest();
    rest.refreshService = refreshService;
    rest.configService = configService;
    rest.authenticationContext = authCtx;
    rest.setSeedServiceForManifest(seedService);
    when(seedService.loadManifest()).thenReturn(List.of(builtin("prov-o", true), builtin("qudt", false)));
  }

  private static OntologyEntry builtin(String id, boolean required) {
    return new OntologyEntry(
      id,
      id,
      id + ".ttl",
      "http://example.org/" + id + "/",
      "Turtle",
      "http://example.org/" + id + ".ttl",
      "CC0 1.0",
      "1",
      "0".repeat(64),
      256L,
      required,
      OntologyEntry.Source.BUILTIN
    );
  }

  private static BundleView view(String id, String source, boolean required, boolean enabled) {
    return new BundleView(id, id, source, required, enabled, "http://example.org/" + id + "/", null, "CC0 1.0", "deadbeef", 100L);
  }

  // ---------- @Annotation gates ---------------------------------------------

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = SemanticAdminRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "SemanticAdminRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2() {
    Path p = SemanticAdminRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/semantic", p.value(), "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ---------- happy path -----------------------------------------------------

  @Test
  void refresh_emptyBody_defaultsToAllBundles_force_false() {
    RefreshOutcome happy = new RefreshOutcome();
    happy.requested = 9;
    happy.refreshed = 7;
    happy.alreadyCurrent = 2;
    happy.errors = new ArrayList<>();
    when(refreshService.refresh(any(), anyBoolean())).thenReturn(happy);

    var r = rest.refreshOntologies(null, securityContext);

    assertEquals(200, r.getStatus());
    RefreshOntologiesResultIO body = (RefreshOntologiesResultIO) r.getEntity();
    assertEquals(9, body.getRequested());
    assertEquals(7, body.getRefreshed());
    assertEquals(2, body.getAlreadyCurrent());
    assertTrue(body.getErrors().isEmpty());

    // Service was invoked with the defaults (empty bundles list, force=false).
    verify(refreshService).refresh(List.of(), false);
  }

  @Test
  void refresh_explicitBundles_andForce_passedThrough() {
    RefreshOutcome happy = new RefreshOutcome();
    happy.requested = 2;
    happy.refreshed = 2;
    happy.alreadyCurrent = 0;
    happy.errors = new ArrayList<>();
    when(refreshService.refresh(any(), anyBoolean())).thenReturn(happy);

    var req = new RefreshOntologiesRequestIO(List.of("prov-o", "qudt"), true);
    var r = rest.refreshOntologies(req, securityContext);

    assertEquals(200, r.getStatus());
    verify(refreshService).refresh(List.of("prov-o", "qudt"), true);
  }

  @Test
  void refresh_partialFailure_returns200_withErrorsArray() {
    RefreshOutcome mixed = new RefreshOutcome();
    mixed.requested = 3;
    mixed.refreshed = 1;
    mixed.alreadyCurrent = 1;
    mixed.errors = List.of(new BundleError("qudt", "Could not fetch http://qudt.org/2.1/vocab/unit.ttl: timeout"));
    when(refreshService.refresh(any(), anyBoolean())).thenReturn(mixed);

    var r = rest.refreshOntologies(new RefreshOntologiesRequestIO(List.of(), false), securityContext);

    assertEquals(200, r.getStatus(), "partial failure stays 200, per the IO shape");
    RefreshOntologiesResultIO body = (RefreshOntologiesResultIO) r.getEntity();
    assertEquals(3, body.getRequested());
    assertEquals(1, body.getRefreshed());
    assertEquals(1, body.getAlreadyCurrent());
    assertEquals(1, body.getErrors().size());
    assertEquals("qudt", body.getErrors().get(0).getBundle());
    assertTrue(body.getErrors().get(0).getReason().contains("timeout"));
  }

  @Test
  void refresh_unknownBundleId_surfacesInErrorsArray() {
    RefreshOutcome outcome = new RefreshOutcome();
    outcome.requested = 1;
    outcome.refreshed = 0;
    outcome.alreadyCurrent = 0;
    outcome.errors = List.of(new BundleError("not-a-bundle", "Unknown bundle id — not present in ontologies-manifest.json."));
    when(refreshService.refresh(any(), anyBoolean())).thenReturn(outcome);

    var r = rest.refreshOntologies(new RefreshOntologiesRequestIO(List.of("not-a-bundle"), false), securityContext);

    assertEquals(200, r.getStatus());
    RefreshOntologiesResultIO body = (RefreshOntologiesResultIO) r.getEntity();
    assertEquals(1, body.getErrors().size());
    assertEquals("not-a-bundle", body.getErrors().get(0).getBundle());
    assertTrue(body.getErrors().get(0).getReason().toLowerCase().contains("unknown"));
  }

  // ---------- auth paths -----------------------------------------------------

  @Test
  void refresh_noPrincipal_returns401Problem() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    var r = rest.refreshOntologies(null, securityContext);

    assertEquals(401, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_AUTH, body.type());
    assertEquals(401, body.status());
  }

  @Test
  void refresh_principalButNoRole_returns403Problem() {
    when(securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);

    var r = rest.refreshOntologies(null, securityContext);

    assertEquals(403, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_AUTH, body.type());
    assertEquals(403, body.status());
  }

  @Test
  void refresh_nullSecurityContext_returns401Problem() {
    var r = rest.refreshOntologies(null, null);

    assertEquals(401, r.getStatus());
  }

  // ---------- request-IO defaults --------------------------------------------

  @Test
  void requestIO_defaultConstructor_yieldsEmptyBundlesAndForceFalse() {
    var io = new RefreshOntologiesRequestIO();
    assertTrue(io.getBundles().isEmpty());
    assertEquals(false, io.isForce());
  }

  @Test
  void requestIO_nullBundles_areNormalisedToEmpty() {
    var io = new RefreshOntologiesRequestIO();
    io.setBundles(null);
    assertTrue(io.getBundles().isEmpty());
  }

  @Test
  void resultIO_nullErrors_areNormalisedToEmpty() {
    var io = new RefreshOntologiesResultIO();
    io.setErrors(null);
    assertTrue(io.getErrors().isEmpty());
  }

  @Test
  void resultIO_getters_setters_roundtrip() {
    var io = new RefreshOntologiesResultIO();
    io.setRequested(5);
    io.setRefreshed(3);
    io.setAlreadyCurrent(1);
    io.setErrors(List.of(new RefreshOntologiesResultIO.Error("x", "boom")));
    assertEquals(5, io.getRequested());
    assertEquals(3, io.getRefreshed());
    assertEquals(1, io.getAlreadyCurrent());
    assertEquals("x", io.getErrors().get(0).getBundle());
    assertEquals("boom", io.getErrors().get(0).getReason());
  }

  @Test
  void resultErrorIO_defaultConstructor_yieldsNullFields() {
    var e = new RefreshOntologiesResultIO.Error();
    assertNull(e.getBundle());
    assertNull(e.getReason());
    e.setBundle("p");
    e.setReason("r");
    assertEquals("p", e.getBundle());
    assertEquals("r", e.getReason());
  }

  // ============================================================================
  //  N1c2 — /v2/admin/semantic/ontologies surface
  // ============================================================================

  // ---------- list -----------------------------------------------------------

  @Test
  void list_happyPath_returnsMergedView() {
    when(configService.listMerged(any())).thenReturn(
      List.of(view("prov-o", "builtin", true, true), view("custom", "user", false, true))
    );

    var r = rest.listOntologies(securityContext);

    assertEquals(200, r.getStatus());
    OntologyBundleListIO body = (OntologyBundleListIO) r.getEntity();
    assertEquals(2, body.getBundles().size());
    assertEquals("prov-o", body.getBundles().get(0).getId());
    assertEquals("user", body.getBundles().get(1).getSource());
  }

  @Test
  void list_emptyMerged_still200() {
    when(configService.listMerged(any())).thenReturn(List.of());
    var r = rest.listOntologies(securityContext);
    assertEquals(200, r.getStatus());
    OntologyBundleListIO body = (OntologyBundleListIO) r.getEntity();
    assertTrue(body.getBundles().isEmpty());
  }

  @Test
  void list_noPrincipal_returns401Problem() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = rest.listOntologies(securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void list_principalButNoRole_returns403Problem() {
    when(securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);
    var r = rest.listOntologies(securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void list_manifestLoadFailure_stillReturnsMergedFromEmptyManifest() {
    when(seedService.loadManifest()).thenThrow(new RuntimeException("boom"));
    when(configService.listMerged(any())).thenReturn(List.of());

    var r = rest.listOntologies(securityContext);

    assertEquals(200, r.getStatus());
  }

  // ---------- disable --------------------------------------------------------

  @Test
  void disable_required_returns409Problem() {
    when(configService.setBundleEnabled(eq("prov-o"), eq(false), any(), any())).thenReturn(
      SetEnabledResult.REQUIRED_CANNOT_DISABLE
    );

    var r = rest.disableOntology("prov-o", securityContext);

    assertEquals(409, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_REQUIRED, body.type());
  }

  @Test
  void disable_unknown_returns404Problem() {
    when(configService.setBundleEnabled(eq("ghost"), eq(false), any(), any())).thenReturn(SetEnabledResult.NOT_FOUND);

    var r = rest.disableOntology("ghost", securityContext);

    assertEquals(404, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_NOT_FOUND, body.type());
  }

  @Test
  void disable_happyPath_returnsMergedRow() {
    when(configService.setBundleEnabled(eq("qudt"), eq(false), any(), any())).thenReturn(SetEnabledResult.OK);
    when(configService.findBundle(eq("qudt"), any())).thenReturn(Optional.of(view("qudt", "builtin", false, false)));

    var r = rest.disableOntology("qudt", securityContext);

    assertEquals(200, r.getStatus());
    OntologyBundleIO body = (OntologyBundleIO) r.getEntity();
    assertEquals("qudt", body.getId());
    assertFalse(body.isEnabled());
  }

  @Test
  void disable_okButFindBundleEmpty_falls_back_to_404() {
    when(configService.setBundleEnabled(eq("qudt"), eq(false), any(), any())).thenReturn(SetEnabledResult.OK);
    when(configService.findBundle(eq("qudt"), any())).thenReturn(Optional.empty());

    var r = rest.disableOntology("qudt", securityContext);

    assertEquals(404, r.getStatus());
  }

  @Test
  void disable_noPrincipal_returns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = rest.disableOntology("prov-o", securityContext);
    assertEquals(401, r.getStatus());
  }

  // ---------- enable ---------------------------------------------------------

  @Test
  void enable_happyPath_returnsMergedRow() {
    when(configService.setBundleEnabled(eq("qudt"), eq(true), any(), any())).thenReturn(SetEnabledResult.OK);
    when(configService.findBundle(eq("qudt"), any())).thenReturn(Optional.of(view("qudt", "builtin", false, true)));

    var r = rest.enableOntology("qudt", securityContext);

    assertEquals(200, r.getStatus());
    OntologyBundleIO body = (OntologyBundleIO) r.getEntity();
    assertTrue(body.isEnabled());
  }

  @Test
  void enable_unknown_returns404Problem() {
    when(configService.setBundleEnabled(eq("ghost"), eq(true), any(), any())).thenReturn(SetEnabledResult.NOT_FOUND);
    var r = rest.enableOntology("ghost", securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void enable_noPrincipal_returns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = rest.enableOntology("prov-o", securityContext);
    assertEquals(401, r.getStatus());
  }

  // ---------- upload ---------------------------------------------------------

  private FileUpload makeFileUpload(byte[] bytes) throws IOException {
    java.nio.file.Path file = tempDir.resolve("upload-" + System.nanoTime() + ".ttl");
    Files.write(file, bytes);
    return new FileUpload() {
      @Override
      public String name() {
        return "file";
      }

      @Override
      public java.nio.file.Path filePath() {
        return file;
      }

      @Override
      public java.nio.file.Path uploadedFile() {
        return file;
      }

      @Override
      public String fileName() {
        return "upload.ttl";
      }

      @Override
      public long size() {
        try {
          return Files.size(file);
        } catch (IOException e) {
          return bytes.length;
        }
      }

      @Override
      public String contentType() {
        return "text/turtle";
      }

      @Override
      public String charSet() {
        return "UTF-8";
      }

      @Override
      public jakarta.ws.rs.core.MultivaluedMap<String, String> getHeaders() {
        return new jakarta.ws.rs.core.MultivaluedHashMap<>();
      }
    };
  }

  @Test
  void upload_happyPath_returns201WithSavedRow() throws Exception {
    String ttl = "@prefix ex: <http://example.org/> .\n";
    FileUpload fu = makeFileUpload(ttl.getBytes(StandardCharsets.UTF_8));
    UserOntologyBundle saved = new UserOntologyBundle();
    saved.setBundleId("custom");
    saved.setSha256("abc");
    saved.setByteSize(42L);
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(UploadResult.created(saved));
    when(configService.findBundle(eq("custom"), any())).thenReturn(
      Optional.of(view("custom", "user", false, true))
    );

    var r = rest.uploadOntology(
      fu,
      "{\"id\":\"custom\",\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );

    assertEquals(201, r.getStatus());
    OntologyBundleIO body = (OntologyBundleIO) r.getEntity();
    assertEquals("custom", body.getId());
    assertEquals("user", body.getSource());
  }

  @Test
  void upload_duplicateId_returns409Problem() throws Exception {
    String ttl = "@prefix ex: <http://example.org/> .\n";
    FileUpload fu = makeFileUpload(ttl.getBytes(StandardCharsets.UTF_8));
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(
      UploadResult.failure(UploadResult.Status.DUPLICATE_ID, "bundle id 'prov-o' shadows a built-in")
    );

    var r = rest.uploadOntology(
      fu,
      "{\"id\":\"prov-o\",\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );

    assertEquals(409, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_DUPLICATE, body.type());
  }

  @Test
  void upload_tooLarge_returns400Problem() throws Exception {
    FileUpload fu = makeFileUpload("@prefix x: <http://x/> .".getBytes(StandardCharsets.UTF_8));
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(
      UploadResult.failure(UploadResult.Status.TOO_LARGE, "payload too big")
    );

    var r = rest.uploadOntology(
      fu,
      "{\"id\":\"custom\",\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );

    assertEquals(400, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_TOO_LARGE, body.type());
  }

  @Test
  void upload_invalidTtl_returns400Problem() throws Exception {
    FileUpload fu = makeFileUpload("not turtle".getBytes(StandardCharsets.UTF_8));
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(
      UploadResult.failure(UploadResult.Status.INVALID_TTL, "payload does not look like Turtle")
    );

    var r = rest.uploadOntology(
      fu,
      "{\"id\":\"custom\",\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );

    assertEquals(400, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_INVALID_TTL, body.type());
  }

  @Test
  void upload_badMetadata_returns400Problem() throws Exception {
    FileUpload fu = makeFileUpload("@prefix x: <http://x/> .".getBytes(StandardCharsets.UTF_8));
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(
      UploadResult.failure(UploadResult.Status.BAD_METADATA, "id required")
    );

    var r = rest.uploadOntology(
      fu,
      "{\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );

    assertEquals(400, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_BAD_METADATA, body.type());
  }

  @Test
  void upload_missingFilePart_returns400Problem() {
    var r = rest.uploadOntology(null, "{\"id\":\"x\"}", securityContext);
    assertEquals(400, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_BAD_METADATA, body.type());
  }

  @Test
  void upload_malformedMetadataJson_returns400Problem() throws Exception {
    FileUpload fu = makeFileUpload("@prefix x: <http://x/> .".getBytes(StandardCharsets.UTF_8));
    var r = rest.uploadOntology(fu, "this is not json", securityContext);
    assertEquals(400, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_BAD_METADATA, body.type());
  }

  @Test
  void upload_noPrincipal_returns401() throws Exception {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    FileUpload fu = makeFileUpload("@prefix x: <http://x/> .".getBytes(StandardCharsets.UTF_8));
    var r = rest.uploadOntology(fu, "{}", securityContext);
    assertEquals(401, r.getStatus());
  }

  // ---------- delete ---------------------------------------------------------

  @Test
  void delete_happyPath_returns204() {
    when(configService.removeBundle(eq("custom"), any(), any())).thenReturn(RemoveResult.REMOVED);
    var r = rest.deleteOntology("custom", securityContext);
    assertEquals(204, r.getStatus());
  }

  @Test
  void delete_builtin_returns409Problem() {
    when(configService.removeBundle(eq("prov-o"), any(), any())).thenReturn(RemoveResult.BUILTIN_NOT_REMOVABLE);
    var r = rest.deleteOntology("prov-o", securityContext);
    assertEquals(409, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_BUILTIN, body.type());
  }

  @Test
  void delete_unknown_returns404Problem() {
    when(configService.removeBundle(eq("ghost"), any(), any())).thenReturn(RemoveResult.NOT_FOUND);
    var r = rest.deleteOntology("ghost", securityContext);
    assertEquals(404, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_BUNDLE_NOT_FOUND, body.type());
  }

  @Test
  void delete_noPrincipal_returns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = rest.deleteOntology("x", securityContext);
    assertEquals(401, r.getStatus());
  }

  // ---------- IO smoke -------------------------------------------------------

  @Test
  void ontologyBundleIO_roundtripsAllFields() {
    var io = new OntologyBundleIO();
    io.setId("x");
    io.setName("X");
    io.setSource("user");
    io.setRequired(false);
    io.setEnabled(true);
    io.setIriPrefix("http://x/");
    io.setCanonicalUrl("http://x.ttl");
    io.setLicense("CC0");
    io.setSha256("abc");
    io.setByteSize(123L);
    assertEquals("x", io.getId());
    assertEquals(123L, io.getByteSize());
    assertEquals("user", io.getSource());
  }

  @Test
  void ontologyBundleIO_fromBundleView_copiesAllFields() {
    BundleView v = view("custom", "user", false, true);
    OntologyBundleIO io = OntologyBundleIO.from(v);
    assertEquals("custom", io.getId());
    assertEquals("user", io.getSource());
    assertEquals(100L, io.getByteSize());
    assertEquals("CC0 1.0", io.getLicense());
  }

  // ---------- coverage: rare branches ---------------------------------------

  @Test
  void upload_createdButFindBundleMissing_returnsFallback201() throws Exception {
    String ttl = "@prefix ex: <http://example.org/> .\n";
    FileUpload fu = makeFileUpload(ttl.getBytes(StandardCharsets.UTF_8));
    UserOntologyBundle saved = new UserOntologyBundle();
    saved.setBundleId("custom");
    saved.setSha256("abc");
    saved.setByteSize(42L);
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(UploadResult.created(saved));
    when(configService.findBundle(eq("custom"), any())).thenReturn(Optional.empty()); // forces fallback

    var r = rest.uploadOntology(
      fu,
      "{\"id\":\"custom\",\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );

    assertEquals(201, r.getStatus());
    OntologyBundleIO body = (OntologyBundleIO) r.getEntity();
    assertEquals("custom", body.getId());
    assertEquals("user", body.getSource());
    assertTrue(body.isEnabled());
  }

  @Test
  void upload_ioError_returns500() throws Exception {
    String ttl = "@prefix ex: <http://example.org/> .\n";
    FileUpload fu = makeFileUpload(ttl.getBytes(StandardCharsets.UTF_8));
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(
      UploadResult.failure(UploadResult.Status.IO_ERROR, "disk full")
    );
    var r = rest.uploadOntology(
      fu,
      "{\"id\":\"custom\",\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );
    assertEquals(500, r.getStatus());
  }

  @Test
  void upload_savedRowHasNullByteSize_fallbackZero() throws Exception {
    String ttl = "@prefix ex: <http://example.org/> .\n";
    FileUpload fu = makeFileUpload(ttl.getBytes(StandardCharsets.UTF_8));
    UserOntologyBundle saved = new UserOntologyBundle();
    saved.setBundleId("custom");
    saved.setSha256(null);
    saved.setByteSize(null);
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(UploadResult.created(saved));
    when(configService.findBundle(eq("custom"), any())).thenReturn(Optional.empty());

    var r = rest.uploadOntology(
      fu,
      "{\"id\":\"custom\",\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );

    assertEquals(201, r.getStatus());
    OntologyBundleIO body = (OntologyBundleIO) r.getEntity();
    assertEquals(0L, body.getByteSize());
  }

  @Test
  void flipEnabled_emptyManifest_loadsAndProceeds() {
    when(seedService.loadManifest()).thenReturn(List.of());
    when(configService.setBundleEnabled(eq("ghost"), eq(true), any(), any())).thenReturn(SetEnabledResult.NOT_FOUND);

    var r = rest.enableOntology("ghost", securityContext);

    assertEquals(404, r.getStatus());
  }

  @Test
  void flipEnabled_manifestLoadFailure_stillProcesses() {
    when(seedService.loadManifest()).thenThrow(new RuntimeException("boom"));
    when(configService.setBundleEnabled(eq("qudt"), eq(true), any(), any())).thenReturn(SetEnabledResult.NOT_FOUND);
    var r = rest.enableOntology("qudt", securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void delete_manifestLoadFailure_stillProcesses() {
    when(seedService.loadManifest()).thenThrow(new RuntimeException("boom"));
    when(configService.removeBundle(eq("custom"), any(), any())).thenReturn(RemoveResult.REMOVED);
    var r = rest.deleteOntology("custom", securityContext);
    assertEquals(204, r.getStatus());
  }

  @Test
  void upload_manifestLoadFailure_stillProcesses() throws Exception {
    when(seedService.loadManifest()).thenThrow(new RuntimeException("boom"));
    FileUpload fu = makeFileUpload("@prefix x: <http://x/> .".getBytes(StandardCharsets.UTF_8));
    UserOntologyBundle saved = new UserOntologyBundle();
    saved.setBundleId("custom");
    when(configService.uploadBundle(any(), any(), any(), any())).thenReturn(UploadResult.created(saved));
    when(configService.findBundle(eq("custom"), any())).thenReturn(Optional.empty());

    var r = rest.uploadOntology(
      fu,
      "{\"id\":\"custom\",\"iriPrefix\":\"http://example.org/\",\"license\":\"CC0 1.0\"}",
      securityContext
    );

    assertEquals(201, r.getStatus());
  }

  @Test
  void upload_blankMetadata_returns400() throws Exception {
    FileUpload fu = makeFileUpload("@prefix x: <http://x/> .".getBytes(StandardCharsets.UTF_8));
    var r = rest.uploadOntology(fu, "", securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void list_lazyInstantiates_seedService_when_notInjected() {
    // Drop the test-injected seed service so the lazy production path runs.
    rest.setSeedServiceForManifest(null);
    when(configService.listMerged(any())).thenReturn(List.of());

    // Production seed service hits the real classpath manifest. We tolerate
    // any RuntimeException from no-classpath-resource — just confirm the
    // endpoint still produces a 200 (manifest-load-failure path is fail-soft).
    var r = rest.listOntologies(securityContext);
    assertEquals(200, r.getStatus());
  }
}
