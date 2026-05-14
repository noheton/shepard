package de.dlr.shepard.plugins.storage.s3.resources;

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
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * FS1b — admin REST surface for the S3-compatible storage plugin.
 *
 * <p>Lives under {@code /v2/admin/storage/s3/...}. Class-level
 * {@code @RolesAllowed("instance-admin")} gate. Responses are
 * {@code application/json}; errors use the RFC 7807
 * {@code application/problem+json} envelope via {@link ProblemJson}.
 *
 * <p>PROV1a captures every mutation through this resource via
 * {@code ProvenanceCaptureFilter}; the filter records request method
 * + path + status only, never response bodies — so the
 * {@code POST .../credential} plaintext never enters the
 * {@code :Activity} audit trail.
 *
 * @see S3StorageConfigService
 * @see S3StorageConfig
 */
@Path("/v2/admin/storage/s3")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class S3StorageAdminRest {

  /** RFC 7807 type URIs for problem responses. */
  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/storage.s3.config.read-only-field";
  static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/storage.s3.config.bad-request";
  static final String PROBLEM_TYPE_UNAVAILABLE = "/problems/storage.s3.config.unavailable";

  @Inject
  S3StorageConfigService service;

  // ─── GET /config ────────────────────────────────────────────────

  @GET
  @Path("/config")
  @Operation(
    summary = "Read the current :S3StorageConfig singleton.",
    description = "Returns the runtime-mutable S3 storage config — enabled, endpointUrl, " +
    "region, bucket, bucketPrefix, forcePathStyle, accessKeyId, sseAlgorithm, " +
    "multipartThresholdBytes, connectionTimeoutSeconds, requestTimeoutSeconds. " +
    "Credential material is masked: secretKeySet + an 8-hex fingerprint of the " +
    "SHA-256 hash are surfaced in place of the secretAccessKey itself."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current S3 storage config (singleton).",
    content = @Content(schema = @Schema(implementation = S3StorageConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    S3StorageConfig cfg = service.current();
    return Response.ok(S3StorageConfigIO.from(cfg)).build();
  }

  // ─── PATCH /config ──────────────────────────────────────────────

  @PATCH
  @Path("/config")
  @Operation(
    summary = "RFC 7396 merge-patch the :S3StorageConfig singleton.",
    description = "Patchable fields: enabled, endpointUrl, region, bucket, bucketPrefix, " +
    "forcePathStyle, sseAlgorithm, multipartThresholdBytes, connectionTimeoutSeconds, " +
    "requestTimeoutSeconds. Credential fields (accessKeyId, secretAccessKeyCipher, " +
    "secretAccessKeyHash, secretKey) are read-only via this path — use " +
    "POST .../credential to set them. PROV1a captures this PATCH as an :Activity row."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = S3StorageConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Caller patched a read-only field or supplied an invalid value.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(S3StorageConfigPatchIO body, @Context SecurityContext security) {
    S3StorageConfigPatchIO patch = body == null ? new S3StorageConfigPatchIO() : body;
    S3Patch svc = new S3Patch();
    svc.enabled = patch.getEnabled();
    svc.endpointUrl = patch.getEndpointUrl();
    svc.endpointUrlTouched = patch.isEndpointUrlTouched();
    svc.region = patch.getRegion();
    svc.regionTouched = patch.isRegionTouched();
    svc.bucket = patch.getBucket();
    svc.bucketTouched = patch.isBucketTouched();
    svc.bucketPrefix = patch.getBucketPrefix();
    svc.bucketPrefixTouched = patch.isBucketPrefixTouched();
    svc.forcePathStyle = patch.getForcePathStyle();
    svc.sseAlgorithm = patch.getSseAlgorithm();
    svc.sseAlgorithmTouched = patch.isSseAlgorithmTouched();
    svc.multipartThresholdBytes = patch.getMultipartThresholdBytes();
    svc.connectionTimeoutSeconds = patch.getConnectionTimeoutSeconds();
    svc.requestTimeoutSeconds = patch.getRequestTimeoutSeconds();
    svc.accessKeyIdTouched = patch.isAccessKeyIdTouched();
    svc.secretAccessKeyCipherTouched = patch.isSecretAccessKeyCipherTouched();
    svc.secretAccessKeyHashTouched = patch.isSecretAccessKeyHashTouched();

    final S3StorageConfig saved;
    try {
      saved = service.patch(svc, callerName(security));
    } catch (ReadOnlyFieldException denied) {
      Log.warnf(
        "FS1b: rejected PATCH /v2/admin/storage/s3/config — read-only field '%s' touched",
        denied.field()
      );
      return problem(
        PROBLEM_TYPE_READ_ONLY_FIELD,
        "Field is read-only via PATCH",
        Status.BAD_REQUEST,
        "Field '" +
        denied.field() +
        "' cannot be set via PATCH. Use POST /v2/admin/storage/s3/credential to mutate it."
      );
    } catch (IllegalArgumentException bad) {
      return problem(
        PROBLEM_TYPE_BAD_REQUEST,
        "Invalid patch value",
        Status.BAD_REQUEST,
        bad.getMessage()
      );
    }
    return Response.ok(S3StorageConfigIO.from(saved)).build();
  }

  // ─── POST /credential ───────────────────────────────────────────

  @POST
  @Path("/credential")
  @Operation(
    summary = "Set or rotate the S3 credential (accessKeyId + secretKey).",
    description = "Body: {\"accessKeyId\": \"...\", \"secretKey\": \"...\"}. The secretKey " +
    "is encrypted with AES-GCM keyed off the shepard instance id and stored on " +
    ":S3StorageConfig. The response carries only the fingerprint (first 8 hex of the " +
    "SHA-256) — the plaintext is never echoed. ProvenanceCaptureFilter captures the " +
    "request method + path + status only, so the plaintext does not enter the audit trail."
  )
  @APIResponse(
    responseCode = "200",
    description = "Credential stored successfully.",
    content = @Content(schema = @Schema(implementation = S3CredentialSetIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Empty / missing accessKeyId or secretKey.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response setCredential(S3CredentialIO body, @Context SecurityContext security) {
    if (body == null || body.accessKeyId() == null || body.accessKeyId().isBlank()) {
      return problem(
        PROBLEM_TYPE_BAD_REQUEST,
        "Empty credential",
        Status.BAD_REQUEST,
        "Body must carry a non-empty 'accessKeyId' field."
      );
    }
    if (body.secretKey() == null || body.secretKey().isBlank()) {
      return problem(
        PROBLEM_TYPE_BAD_REQUEST,
        "Empty credential",
        Status.BAD_REQUEST,
        "Body must carry a non-empty 'secretKey' field."
      );
    }
    S3StorageConfig saved = service.setCredential(body.accessKeyId(), body.secretKey(), callerName(security));
    S3CredentialSetIO out = new S3CredentialSetIO(
      true,
      S3StorageConfigService.fingerprint(saved.getSecretAccessKeyHash())
    );
    return Response.ok(out).build();
  }

  // ─── DELETE /credential ─────────────────────────────────────────

  @DELETE
  @Path("/credential")
  @Operation(
    summary = "Clear the stored S3 credential.",
    description = "Wipes :S3StorageConfig.secretAccessKeyCipher + .secretAccessKeyHash + " +
    ".accessKeyId. Subsequent put/get/delete calls throw storage.provider.unavailable " +
    "until fresh credentials are set. The action is captured as an :Activity row via PROV1a."
  )
  @APIResponse(
    responseCode = "200",
    description = "Credential cleared.",
    content = @Content(schema = @Schema(implementation = S3StorageConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response clearCredential(@Context SecurityContext security) {
    S3StorageConfig saved = service.clearCredential(callerName(security));
    return Response.ok(S3StorageConfigIO.from(saved)).build();
  }

  // ─── POST /test-connection ──────────────────────────────────────

  @POST
  @Path("/test-connection")
  @Operation(
    summary = "Diagnose connectivity to the configured S3 endpoint and bucket.",
    description = "Issues a HeadBucketRequest probe and reports reachable/statusCode/latency. " +
    "Useful for operators to verify config before enabling storage."
  )
  @APIResponse(
    responseCode = "200",
    description = "Diagnostic result. reachable=true means the bucket was reachable; " +
    "reachable=false means network or auth error.",
    content = @Content(schema = @Schema(implementation = S3TestConnectionIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response testConnection() {
    S3StorageConfig cfg = service.current();
    String bucket = cfg.getBucket();
    String endpoint = cfg.getEndpointUrl();

    if (bucket == null || bucket.isBlank()) {
      return Response.ok(
        new S3TestConnectionIO(false, 0, 0L, endpoint, null, "bucket is not configured")
      ).build();
    }

    S3Client client;
    try {
      client = buildClientForProbe(cfg);
    } catch (StorageProviderUnavailableException e) {
      return Response.ok(
        new S3TestConnectionIO(false, 0, 0L, endpoint, bucket, e.getMessage())
      ).build();
    }

    HeadBucketRequest headBucket = HeadBucketRequest.builder()
      .bucket(bucket)
      .build();

    long t0 = System.nanoTime();
    try {
      client.headBucket(headBucket);
      long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
      return Response.ok(
        new S3TestConnectionIO(true, 200, latencyMs, endpoint, bucket, null)
      ).build();
    } catch (S3Exception e) {
      long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
      // 403 means the endpoint is reachable but we lack access;
      // 404 means the bucket doesn't exist but the endpoint is up.
      // Both count as "reachable endpoint" for triage purposes.
      boolean endpointReachable = e.statusCode() >= 400 && e.statusCode() < 600;
      return Response.ok(
        new S3TestConnectionIO(endpointReachable, e.statusCode(), latencyMs, endpoint, bucket, e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage())
      ).build();
    } catch (Exception e) {
      long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
      return Response.ok(
        new S3TestConnectionIO(false, 0, latencyMs, endpoint, bucket, e.getMessage())
      ).build();
    }
  }

  // ─── helpers ────────────────────────────────────────────────────

  private S3Client buildClientForProbe(S3StorageConfig cfg) throws StorageProviderUnavailableException {
    // Build a client for the test-connection probe. Uses the same
    // logic as S3FileStorage.buildClient but inlined here to avoid
    // circular dependency on S3FileStorage from the admin REST.
    String accessKeyId = cfg.getAccessKeyId();
    String secretKey;
    try {
      secretKey = service.resolvePlaintextSecret();
    } catch (IllegalStateException e) {
      throw new StorageProviderUnavailableException(
        "S3 credential decryption failed: " + e.getMessage(),
        e
      );
    }
    if (accessKeyId == null || accessKeyId.isBlank() || secretKey == null || secretKey.isBlank()) {
      throw new StorageProviderUnavailableException(
        "S3 credentials not configured — use POST /v2/admin/storage/s3/credential"
      );
    }
    return buildS3Client(cfg, accessKeyId, secretKey);
  }

  static S3Client buildS3Client(S3StorageConfig cfg, String accessKeyId, String secretKey) {
    software.amazon.awssdk.services.s3.S3ClientBuilder builder = software.amazon.awssdk.services.s3.S3Client.builder()
      .credentialsProvider(
        software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
          software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKeyId, secretKey)
        )
      )
      .region(software.amazon.awssdk.regions.Region.of(
        cfg.getRegion() != null && !cfg.getRegion().isBlank() ? cfg.getRegion() : "us-east-1"
      ))
      .serviceConfiguration(
        software.amazon.awssdk.services.s3.S3Configuration.builder()
          .pathStyleAccessEnabled(cfg.isForcePathStyle())
          .build()
      )
      .httpClient(
        software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder()
          .connectionTimeout(java.time.Duration.ofSeconds(cfg.getConnectionTimeoutSeconds()))
          .socketTimeout(java.time.Duration.ofSeconds(cfg.getRequestTimeoutSeconds()))
          .build()
      );

    String endpointUrl = cfg.getEndpointUrl();
    if (endpointUrl != null && !endpointUrl.isBlank()) {
      builder.endpointOverride(java.net.URI.create(endpointUrl));
    }
    return builder.build();
  }

  private static String callerName(SecurityContext security) {
    if (security == null) return "unknown";
    var p = security.getUserPrincipal();
    if (p == null) return "unknown";
    String name = p.getName();
    return (name == null || name.isBlank()) ? "unknown" : name;
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
