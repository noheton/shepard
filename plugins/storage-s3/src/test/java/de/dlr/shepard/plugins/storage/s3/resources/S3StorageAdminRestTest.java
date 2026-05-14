package de.dlr.shepard.plugins.storage.s3.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.storage.s3.entities.S3StorageConfig;
import de.dlr.shepard.plugins.storage.s3.io.S3CredentialIO;
import de.dlr.shepard.plugins.storage.s3.io.S3CredentialSetIO;
import de.dlr.shepard.plugins.storage.s3.io.S3StorageConfigIO;
import de.dlr.shepard.plugins.storage.s3.io.S3StorageConfigPatchIO;
import de.dlr.shepard.plugins.storage.s3.io.S3TestConnectionIO;
import de.dlr.shepard.plugins.storage.s3.services.S3StorageConfigService;
import de.dlr.shepard.plugins.storage.s3.services.S3StorageConfigService.ReadOnlyFieldException;
import de.dlr.shepard.plugins.storage.s3.services.S3StorageConfigService.S3Patch;
import de.dlr.shepard.storage.StorageProviderUnavailableException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * FS1b — unit tests for the admin REST surface.
 * No Quarkus boot — service is mocked.
 */
class S3StorageAdminRestTest {

  private S3StorageConfigService service;
  private S3StorageAdminRest rest;
  private SecurityContext security;

  @BeforeEach
  void setUp() {
    service = mock(S3StorageConfigService.class);
    rest = new S3StorageAdminRest();
    rest.service = service;
    security = mock(SecurityContext.class);
    Principal p = mock(Principal.class);
    when(p.getName()).thenReturn("operator-alice");
    when(security.getUserPrincipal()).thenReturn(p);
  }

  // ─── annotation gates ───────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = S3StorageAdminRest.class.getAnnotation(RolesAllowed.class);
    assertThat(gate).isNotNull();
    assertThat(gate.value()).containsExactly(Constants.INSTANCE_ADMIN_ROLE);
  }

  @Test
  void pathIsV2() {
    Path p = S3StorageAdminRest.class.getAnnotation(Path.class);
    assertThat(p).isNotNull();
    assertThat(p.value()).isEqualTo("/v2/admin/storage/s3");
  }

  // ─── GET /config ────────────────────────────────────────────────────────

  @Test
  void getConfig_returnsMaskedIO() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setEnabled(true);
    cfg.setEndpointUrl("https://garage.example.org");
    cfg.setRegion("eu-central-1");
    cfg.setBucket("my-bucket");
    cfg.setAccessKeyId("AKIA123");
    cfg.setSecretAccessKeyCipher("gcm1:ciphertext");
    cfg.setSecretAccessKeyHash("0123456789abcdef".repeat(4));
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertThat(r.getStatus()).isEqualTo(200);
    S3StorageConfigIO body = (S3StorageConfigIO) r.getEntity();
    assertThat(body.enabled()).isTrue();
    assertThat(body.bucket()).isEqualTo("my-bucket");
    assertThat(body.accessKeyId()).isEqualTo("AKIA123");
    assertThat(body.secretKeySet()).isTrue();
    assertThat(body.secretKeyFingerprint()).isEqualTo("01234567");

    // CRITICAL — cipher and full hash must NEVER appear in the IO.
    String rendered = body.toString();
    assertThat(rendered).doesNotContain("gcm1:ciphertext");
    assertThat(rendered).doesNotContain("0123456789abcdef0123456789abcdef");
  }

  @Test
  void getConfig_secretKeySetFalseWhenNoCredential() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setEnabled(false);
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    S3StorageConfigIO body = (S3StorageConfigIO) r.getEntity();
    assertThat(body.secretKeySet()).isFalse();
    assertThat(body.secretKeyFingerprint()).isNull();
  }

  // ─── PATCH /config ──────────────────────────────────────────────────────

  @Test
  void patchConfig_appliesAndReturnsMaskedIO() {
    S3StorageConfig saved = new S3StorageConfig();
    saved.setEnabled(true);
    saved.setBucket("my-bucket");
    when(service.patch(any(S3Patch.class), anyString())).thenReturn(saved);

    S3StorageConfigPatchIO patch = new S3StorageConfigPatchIO();
    patch.setEnabled(true);

    Response r = rest.patchConfig(patch, security);

    assertThat(r.getStatus()).isEqualTo(200);
    S3StorageConfigIO body = (S3StorageConfigIO) r.getEntity();
    assertThat(body.enabled()).isTrue();
  }

  @Test
  void patchConfig_acceptsNullBodyAsNoOp() {
    S3StorageConfig saved = new S3StorageConfig();
    when(service.patch(any(S3Patch.class), anyString())).thenReturn(saved);

    Response r = rest.patchConfig(null, security);

    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void patchConfig_rejectsAccessKeyIdWithProblemJson() {
    when(service.patch(any(S3Patch.class), anyString()))
      .thenThrow(new ReadOnlyFieldException("accessKeyId"));

    S3StorageConfigPatchIO patch = new S3StorageConfigPatchIO();
    patch.setAccessKeyId("anything");

    Response r = rest.patchConfig(patch, security);

    assertThat(r.getStatus()).isEqualTo(400);
    assertThat(r.getMediaType().toString()).contains("application/problem+json");
    ProblemJson body = (ProblemJson) r.getEntity();
    assertThat(body.title()).contains("read-only");
    assertThat(body.detail()).contains("accessKeyId");
  }

  @Test
  void patchConfig_rejectsInvalidValueWithProblemJson() {
    when(service.patch(any(S3Patch.class), anyString()))
      .thenThrow(new IllegalArgumentException("multipartThresholdBytes must be > 0"));

    S3StorageConfigPatchIO patch = new S3StorageConfigPatchIO();
    patch.setMultipartThresholdBytes(-1L);

    Response r = rest.patchConfig(patch, security);

    assertThat(r.getStatus()).isEqualTo(400);
    ProblemJson body = (ProblemJson) r.getEntity();
    assertThat(body.title()).contains("Invalid");
  }

  // ─── POST /credential ───────────────────────────────────────────────────

  @Test
  void setCredential_returnsFingerprint() {
    S3StorageConfig saved = new S3StorageConfig();
    saved.setAccessKeyId("AKIA123");
    saved.setSecretAccessKeyHash("deadbeef".repeat(8));
    when(service.setCredential(anyString(), anyString(), anyString())).thenReturn(saved);

    S3CredentialIO body = new S3CredentialIO("AKIA123", "my-secret");

    Response r = rest.setCredential(body, security);

    assertThat(r.getStatus()).isEqualTo(200);
    S3CredentialSetIO out = (S3CredentialSetIO) r.getEntity();
    assertThat(out.secretKeySet()).isTrue();
    assertThat(out.secretKeyFingerprint()).isEqualTo("deadbeef");
  }

  @Test
  void setCredential_rejectsMissingAccessKeyId() {
    Response r = rest.setCredential(new S3CredentialIO("", "secret"), security);

    assertThat(r.getStatus()).isEqualTo(400);
    assertThat(r.getMediaType().toString()).contains("problem+json");
    ProblemJson body = (ProblemJson) r.getEntity();
    assertThat(body.detail()).contains("accessKeyId");
  }

  @Test
  void setCredential_rejectsMissingSecretKey() {
    Response r = rest.setCredential(new S3CredentialIO("AKIA123", ""), security);

    assertThat(r.getStatus()).isEqualTo(400);
    ProblemJson body = (ProblemJson) r.getEntity();
    assertThat(body.detail()).contains("secretKey");
  }

  @Test
  void setCredential_rejectsNullBody() {
    Response r = rest.setCredential(null, security);

    assertThat(r.getStatus()).isEqualTo(400);
  }

  // ─── DELETE /credential ─────────────────────────────────────────────────

  @Test
  void clearCredential_returnsUpdatedConfig() {
    S3StorageConfig saved = new S3StorageConfig();
    saved.setAccessKeyId("");
    when(service.clearCredential(anyString())).thenReturn(saved);

    Response r = rest.clearCredential(security);

    assertThat(r.getStatus()).isEqualTo(200);
    S3StorageConfigIO body = (S3StorageConfigIO) r.getEntity();
    assertThat(body.secretKeySet()).isFalse();
  }

  // ─── POST /test-connection ──────────────────────────────────────────────

  @Test
  void testConnection_returnsUnreachableWhenBucketBlank() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setBucket("");
    when(service.current()).thenReturn(cfg);

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    S3TestConnectionIO body = (S3TestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isFalse();
    assertThat(body.detail()).contains("bucket");
  }

  @Test
  void testConnection_returnsUnreachableWhenNoCredentials() throws Exception {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setBucket("my-bucket");
    cfg.setAccessKeyId("");
    when(service.current()).thenReturn(cfg);
    when(service.resolvePlaintextSecret()).thenReturn(null);

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    S3TestConnectionIO body = (S3TestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isFalse();
  }
}
