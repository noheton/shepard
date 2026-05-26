package de.dlr.shepard.v2.admin.semantic;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.v2.admin.semantic.io.PatchSemanticConfigIO;
import de.dlr.shepard.v2.admin.semantic.io.SemanticConfigIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SEMA-V6-003 — admin REST endpoint for the {@link SemanticConfig} singleton.
 *
 * <p>Provides {@code GET /v2/admin/semantic/config} (read the current config)
 * and {@code PATCH /v2/admin/semantic/config} (RFC 7396 merge-patch, null =
 * leave unchanged). Extends the {@code /v2/admin/semantic} surface introduced
 * by N1c ({@link SemanticAdminRest}) without touching the existing ontology-
 * bundle endpoints.
 *
 * <p>Auth: instance-admin role required on both methods.
 */
@Path("/v2/admin/semantic/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class SemanticConfigRest {

  static final String PROBLEM_TYPE_AUTH = "/problems/auth.denied";
  static final String PROBLEM_TYPE_BAD_MODE = "/problems/semantic.config.bad-annotation-mode";
  static final String PROBLEM_TYPE_BAD_DELETE_POLICY = "/problems/semantic.config.bad-annotation-delete-policy";

  static final java.util.Set<String> VALID_DELETE_POLICIES = java.util.Set.of(
    "author-or-manager", "author-only", "manager-only"
  );

  @Inject
  OntologyConfigService configService;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Defence-in-depth role check — mirrors {@link SemanticAdminRest}.
   */
  private static void requireInstanceAdmin(SecurityContext sc) {
    if (sc == null || sc.getUserPrincipal() == null) {
      throw new InvalidAuthException("Authentication required");
    }
    if (!sc.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)) {
      throw new InvalidAuthException("instance-admin role required");
    }
  }

  private Response guardAdmin(SecurityContext sc) {
    try {
      requireInstanceAdmin(sc);
      return null;
    } catch (InvalidAuthException denied) {
      Status status = denied.getMessage() != null && denied.getMessage().contains("Authentication required")
        ? Status.UNAUTHORIZED
        : Status.FORBIDDEN;
      return problem(PROBLEM_TYPE_AUTH, denied.getMessage(), status, denied.getMessage());
    }
  }

  // ─── GET ──────────────────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "Get the current SemanticConfig singleton.",
    description = "Returns all operator-visible semantic config fields including the four " +
    "SEMA-V6-003 additions: defaultVocabularyAppId, annotationMode, suggestionEnabled, " +
    "suggestionModelId. First-starts the singleton from deploy-time defaults if not yet seeded."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current semantic config.",
    content = @Content(schema = @Schema(implementation = SemanticConfigIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (RFC 7807).")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role (RFC 7807).")
  public Response getConfig(@Context SecurityContext securityContext) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    SemanticConfig cfg = configService.loadSingleton();
    return Response.ok(SemanticConfigIO.from(cfg)).build();
  }

  // ─── PATCH ────────────────────────────────────────────────────────────────

  @PATCH
  @Operation(
    summary = "Merge-patch the SemanticConfig singleton.",
    description = "RFC 7396: null fields in the request body are ignored (leave unchanged). " +
    "Only fields present with non-null values are applied. Supported merge targets: " +
    "preseedEnabled, disabledBundles, defaultVocabularyAppId, annotationMode, " +
    "suggestionEnabled, suggestionModelId."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated semantic config.",
    content = @Content(schema = @Schema(implementation = SemanticConfigIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid annotationMode value (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required (RFC 7807).")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role (RFC 7807).")
  public Response patchConfig(PatchSemanticConfigIO patch, @Context SecurityContext securityContext) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    if (patch == null) {
      // Empty PATCH body — no-op; return current state.
      SemanticConfig cfg = configService.loadSingleton();
      return Response.ok(SemanticConfigIO.from(cfg)).build();
    }

    // Validate annotationMode before loading singleton to avoid partial mutation.
    if (patch.getAnnotationMode() != null) {
      String mode = patch.getAnnotationMode().trim().toUpperCase();
      if (!mode.equals("STRICT") && !mode.equals("PERMISSIVE")) {
        return problem(
          PROBLEM_TYPE_BAD_MODE,
          "Invalid annotationMode",
          Status.BAD_REQUEST,
          "annotationMode must be 'STRICT' or 'PERMISSIVE'; got: " + patch.getAnnotationMode()
        );
      }
    }

    // Validate annotationDeletePolicy before loading singleton.
    if (patch.getAnnotationDeletePolicy() != null && !patch.getAnnotationDeletePolicy().isBlank()) {
      String policy = patch.getAnnotationDeletePolicy().trim().toLowerCase();
      if (!VALID_DELETE_POLICIES.contains(policy)) {
        return problem(
          PROBLEM_TYPE_BAD_DELETE_POLICY,
          "Invalid annotationDeletePolicy",
          Status.BAD_REQUEST,
          "annotationDeletePolicy must be one of 'author-or-manager', 'author-only', 'manager-only'; got: "
            + patch.getAnnotationDeletePolicy()
        );
      }
    }

    SemanticConfig cfg = configService.loadSingleton();
    String actor = callerName(securityContext);

    // Apply merge-patch: only non-null fields in the patch body are applied.
    if (patch.getPreseedEnabled() != null) {
      cfg.setPreseedEnabled(patch.getPreseedEnabled());
    }
    if (patch.getDisabledBundles() != null) {
      cfg.setDisabledBundles(new ArrayList<>(patch.getDisabledBundles()));
    }
    if (patch.getDefaultVocabularyAppId() != null) {
      cfg.setDefaultVocabularyAppId(
        patch.getDefaultVocabularyAppId().isBlank() ? null : patch.getDefaultVocabularyAppId()
      );
    }
    if (patch.getAnnotationMode() != null) {
      cfg.setAnnotationMode(patch.getAnnotationMode().trim().toUpperCase());
    }
    if (patch.getSuggestionEnabled() != null) {
      cfg.setSuggestionEnabled(patch.getSuggestionEnabled());
    }
    if (patch.getSuggestionModelId() != null) {
      cfg.setSuggestionModelId(
        patch.getSuggestionModelId().isBlank() ? null : patch.getSuggestionModelId()
      );
    }
    if (patch.getAnnotationDeletePolicy() != null) {
      // Empty string = clear (revert to default); otherwise normalise to lower-case.
      cfg.setAnnotationDeletePolicy(
        patch.getAnnotationDeletePolicy().isBlank() ? null : patch.getAnnotationDeletePolicy().trim().toLowerCase()
      );
    }

    long now = System.currentTimeMillis();
    cfg.setUpdatedAt(now);
    cfg.setUpdatedBy(actor);

    SemanticConfig saved = configService.patchConfig(cfg);
    Log.infof(
      "SemanticConfigRest: config updated by '%s' (preseedEnabled=%b, annotationMode=%s, suggestionEnabled=%b, annotationDeletePolicy=%s)",
      actor, saved.isPreseedEnabled(), saved.getAnnotationMode(), saved.isSuggestionEnabled(),
      saved.getAnnotationDeletePolicy()
    );
    return Response.ok(SemanticConfigIO.from(saved)).build();
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private static String callerName(SecurityContext sc) {
    if (sc == null || sc.getUserPrincipal() == null) return null;
    String n = sc.getUserPrincipal().getName();
    return n == null || n.isBlank() ? null : n;
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
