package de.dlr.shepard.v2.vocabularies.resources;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.vocabularies.io.PersonalVocabularyRequestIO;
import de.dlr.shepard.v2.vocabularies.io.VocabularyIO;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SEMA-V6-014 — REST endpoints for personal vocabulary minting.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@code POST /v2/vocabularies/personal} — mint a new personal vocabulary.
 *       Requires the {@code personalVocabulariesEnabled} flag in
 *       {@code :SemanticConfig} to be {@code true} (403 when disabled).</li>
 *   <li>{@code GET /v2/vocabularies/personal} — list the caller's personal
 *       vocabularies.</li>
 * </ul>
 *
 * <p>Auth: {@code @Authenticated} — any authenticated user; no instance-admin
 * role required. The operator opt-in is the
 * {@code personalVocabulariesEnabled} toggle.
 *
 * <p>URI shape: {@code urn:shepard:personal:<userAppId>:<name>}.
 * The {@code name} segment must match {@link #NAME_PATTERN}.
 * Duplicate detection: {@link VocabularyDAO#findByUri(String)} pre-checks
 * before save to return a clean 409 instead of propagating the V72 unique
 * constraint as a 500.
 */
@Path("/v2/vocabularies/personal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Vocabularies")
public class PersonalVocabularyRest {

  /** URL-safe slug pattern for the personal vocabulary name segment. */
  static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

  static final String PROBLEM_TYPE_DISABLED  = "/problems/semantic.personal-vocab.feature-disabled";
  static final String PROBLEM_TYPE_BAD_NAME  = "/problems/semantic.personal-vocab.bad-name";
  static final String PROBLEM_TYPE_CONFLICT  = "/problems/semantic.personal-vocab.duplicate";
  static final String PROBLEM_TYPE_NO_USER   = "/problems/semantic.personal-vocab.user-not-found";
  static final String PERSONAL_TYPE          = "PERSONAL";

  @Inject
  OntologyConfigService configService;

  @Inject
  VocabularyDAO vocabDAO;

  @Inject
  UserDAO userDAO;

  @Inject
  AuthenticationContext authCtx;

  // ─── POST ─────────────────────────────────────────────────────────────────

  @POST
  @Operation(
    operationId = "createVocabularyTerm",
    summary = "Mint a personal vocabulary.",
    description = "Creates a :Vocabulary node with type=PERSONAL scoped to the calling user. " +
      "URI format: urn:shepard:personal:<userAppId>:<name>. " +
      "Requires the personalVocabulariesEnabled flag to be true in SemanticConfig. " +
      "Returns 403 when the feature is disabled. Returns 409 when a vocabulary with the same name already exists for this user."
  )
  @APIResponse(
    responseCode = "201",
    description = "Personal vocabulary created.",
    content = @Content(schema = @Schema(implementation = VocabularyIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid name (must match [a-z0-9][a-z0-9_-]{0,63}).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Feature disabled by operator (personalVocabulariesEnabled=false).")
  @APIResponse(responseCode = "409", description = "A personal vocabulary with this name already exists for the caller.")
  public Response create(PersonalVocabularyRequestIO body) {
    // Feature gate
    SemanticConfig cfg = configService.loadSingleton();
    if (!cfg.isPersonalVocabulariesEnabled()) {
      return problem(
        PROBLEM_TYPE_DISABLED,
        "Personal vocabularies disabled",
        Status.FORBIDDEN,
        "The personalVocabulariesEnabled flag is false. Ask an instance admin to enable it."
      );
    }

    // Validate request body
    if (body == null || body.getName() == null || body.getName().isBlank()) {
      return problem(PROBLEM_TYPE_BAD_NAME, "Bad name", Status.BAD_REQUEST,
        "name is required and must be non-blank.");
    }
    String name = body.getName().trim();
    if (!NAME_PATTERN.matcher(name).matches()) {
      return problem(PROBLEM_TYPE_BAD_NAME, "Bad name", Status.BAD_REQUEST,
        "name must match [a-z0-9][a-z0-9_-]{0,63}; got: " + name);
    }

    // Resolve caller → userAppId
    String username = authCtx.getCurrentUserName();
    User user = username == null ? null : userDAO.find(username);
    if (user == null || user.getAppId() == null || user.getAppId().isBlank()) {
      return problem(PROBLEM_TYPE_NO_USER, "User not found", Status.FORBIDDEN,
        "Could not resolve caller's user record. Ensure your user profile has been initialised.");
    }
    String userAppId = user.getAppId();

    // Build canonical URI and pre-check uniqueness
    String uri = "urn:shepard:personal:" + userAppId + ":" + name;
    Vocabulary existing = vocabDAO.findByUri(uri);
    if (existing != null) {
      return problem(PROBLEM_TYPE_CONFLICT, "Duplicate vocabulary", Status.CONFLICT,
        "A personal vocabulary named '" + name + "' already exists for this user. URI: " + uri);
    }

    // Mint the vocabulary
    Vocabulary vocab = new Vocabulary();
    vocab.setUri(uri);
    vocab.setLabel(name);
    vocab.setPrefix(null);
    vocab.setDescription(body.getDescription());
    vocab.setEnabled(true);
    vocab.setType(PERSONAL_TYPE);
    vocab.setOwnedByUserAppId(userAppId);
    vocab.setCreatedAt(System.currentTimeMillis());

    Vocabulary saved = vocabDAO.createOrUpdate(vocab);
    Log.infof(
      "PersonalVocabularyRest: personal vocabulary '%s' created by user '%s' (appId=%s, vocabAppId=%s).",
      name, username, userAppId, saved.getAppId()
    );
    return Response.status(Status.CREATED).entity(VocabularyIO.from(saved)).build();
  }

  // ─── GET ──────────────────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listVocabularyTerms",
    summary = "List the caller's personal vocabularies.",
    description = "Returns :Vocabulary nodes with type=PERSONAL owned by the calling user. " +
      "Returns an empty list when the feature is disabled or the user has no personal vocabularies.\n\n" +
      "Pagination: `page` (0-based, default 0) and `pageSize` (1–200, default 50). "
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Response body `total` carries the count.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(name = "X-Total-Count", description = "Total count before paging.", schema = @Schema(implementation = Long.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
  ) {
    String username = authCtx.getCurrentUserName();
    User user = username == null ? null : userDAO.find(username);
    if (user == null || user.getAppId() == null || user.getAppId().isBlank()) {
      return Response.ok(new PagedResponseIO<>(List.of(), 0L, page, pageSize))
        .header("X-Total-Count", 0L)
        .build();
    }
    long total = vocabDAO.countPersonalByOwner(user.getAppId());
    int skip = (int) Math.min((long) page * pageSize, total);
    List<Vocabulary> vocabs = vocabDAO.listPersonalByOwner(user.getAppId(), skip, pageSize);
    List<VocabularyIO> items = vocabs.stream().map(VocabularyIO::from).toList();
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize))
      .header("X-Total-Count", total)
      .build();
  }

}
