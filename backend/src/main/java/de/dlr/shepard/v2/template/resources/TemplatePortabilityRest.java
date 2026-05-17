package de.dlr.shepard.v2.template.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateBodyValidator;
import de.dlr.shepard.template.services.TemplateBodyValidator.InvalidTemplateBodyException;
import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * YAML round-trip import / export for {@link ShepardTemplate} — the
 * "L3 portability story" (T1f in {@code aidocs/16}).
 *
 * <ul>
 *   <li>{@code GET /v2/templates/export} — dumps all non-retired
 *       templates as a YAML document that is round-trippable via the
 *       import endpoint.</li>
 *   <li>{@code POST /v2/templates/import} — ingests a YAML list of
 *       templates, validates each body via {@link TemplateBodyValidator},
 *       and mints new entities (always at version 1 unless a live
 *       template with the same {@code name + templateKind} already
 *       exists, in which case copy-on-write produces the next
 *       version).</li>
 * </ul>
 *
 * <p>Both endpoints are {@code @RolesAllowed("instance-admin")} —
 * export carries full body payloads and import mutates the global
 * template registry, so non-admin access is refused at the JAX-RS
 * filter level.
 *
 * <p>YAML library: {@code jackson-dataformat-yaml}. The
 * {@code YAMLMapper} is constructed with
 * {@link YAMLGenerator.Feature#SPLIT_LINES} disabled to avoid the
 * body string being reflowed across multiple lines, which would break
 * the round-trip test.
 */
@Path("/v2/templates")
@RequestScoped
@Tag(name = "Templates (v2)")
public class TemplatePortabilityRest {

  static final String MEDIA_TYPE_YAML = "text/yaml";
  static final String MEDIA_TYPE_APPLICATION_YAML = "application/yaml";

  /** Shared YAML mapper — stateless after construction, safe to reuse. */
  private static final ObjectMapper YAML_MAPPER = buildYamlMapper();

  @Inject
  ShepardTemplateDAO dao;

  @Inject
  TemplateBodyValidator bodyValidator;

  // -----------------------------------------------------------------------
  // Export
  // -----------------------------------------------------------------------

  @GET
  @Path("/export")
  @Produces({ MEDIA_TYPE_YAML, MEDIA_TYPE_APPLICATION_YAML })
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Operation(
    summary = "Export all templates as a YAML document (admin-only, T1f).",
    description = "Returns every non-retired ShepardTemplate as a YAML list. " +
    "The output is directly importable via POST /v2/templates/import. " +
    "Set ?includeRetired=true to also export retired templates."
  )
  @APIResponse(
    responseCode = "200",
    description = "YAML document; Content-Disposition: attachment; filename=\"shepard-templates.yaml\".",
    content = @Content(mediaType = MEDIA_TYPE_YAML, schema = @Schema(type = SchemaType.STRING))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "500", description = "YAML serialisation failed.")
  public Response export(
    @Parameter(description = "Include retired templates in the export.") @DefaultValue("false")
    @QueryParam("includeRetired") boolean includeRetired,
    @Context SecurityContext securityContext
  ) {
    // Manual 401 check mirrors the list() pattern in ShepardTemplateRest —
    // @RolesAllowed handles 403; the unauthenticated path needs an explicit guard
    // so unit tests can verify 401 without spinning up a full Quarkus context.
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    List<ShepardTemplate> templates = dao.list(null, includeRetired);
    List<TemplateExportEntry> entries = templates.stream().map(TemplateExportEntry::from).toList();

    String yaml;
    try {
      yaml = YAML_MAPPER.writeValueAsString(entries);
    } catch (JsonProcessingException e) {
      return Response.serverError()
        .entity(Map.of("error", "YAML serialisation failed: " + e.getMessage()))
        .build();
    }

    return Response
      .ok(yaml, MEDIA_TYPE_YAML + "; charset=UTF-8")
      .header("Content-Disposition", "attachment; filename=\"shepard-templates.yaml\"")
      .build();
  }

  // -----------------------------------------------------------------------
  // Import
  // -----------------------------------------------------------------------

  @POST
  @Path("/import")
  @Consumes({ MEDIA_TYPE_YAML, MEDIA_TYPE_APPLICATION_YAML })
  @Produces("application/json")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Operation(
    summary = "Import templates from a YAML document (admin-only, T1f).",
    description = "Accepts a YAML list of templates in the same shape as GET /v2/templates/export. " +
    "Each entry is body-validated via TemplateBodyValidator. " +
    "appIds are never reused — import always mints new entities. " +
    "If a live (non-retired) template with the same name + templateKind already exists, " +
    "import mints the next copy-on-write version and retires the prior row. " +
    "Returns 200 + JSON array of created ShepardTemplateIO objects. " +
    "Returns 400 if the YAML cannot be parsed or any template body fails validation."
  )
  @APIResponse(
    responseCode = "200",
    description = "JSON array of the created ShepardTemplateIO objects.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ShepardTemplateIO.class))
  )
  @APIResponse(responseCode = "400", description = "Parse error or body-validation failure.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response importTemplates(String yamlBody, @Context SecurityContext securityContext) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // Treat null or blank body as an empty document → return 200 with empty list.
    if (yamlBody == null || yamlBody.isBlank()) {
      return Response.ok(List.of()).build();
    }

    // Parse the YAML payload.
    List<TemplateExportEntry> entries;
    try {
      entries = YAML_MAPPER.readValue(
        yamlBody,
        YAML_MAPPER.getTypeFactory().constructCollectionType(List.class, TemplateExportEntry.class)
      );
    } catch (JsonProcessingException e) {
      int line = e.getLocation() != null ? (int) e.getLocation().getLineNr() : -1;
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Map.of("error", "YAML parse error: " + e.getOriginalMessage(), "line", line))
        .build();
    }

    if (entries == null || entries.isEmpty()) {
      return Response.ok(List.of()).build();
    }

    String caller = securityContext.getUserPrincipal().getName();
    long now = System.currentTimeMillis();
    List<ShepardTemplateIO> created = new ArrayList<>();

    for (int i = 0; i < entries.size(); i++) {
      TemplateExportEntry entry = entries.get(i);

      // Validate required fields.
      if (entry.getName() == null || entry.getTemplateKind() == null || entry.getBody() == null) {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "entry[" + i + "]: name, templateKind, body are required", "line", i + 1))
          .build();
      }

      // Validate the JSON body DSL.
      try {
        bodyValidator.validate(entry.getBody(), entry.getTemplateKind());
      } catch (InvalidTemplateBodyException e) {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "entry[" + i + "] body invalid: " + String.join("; ", e.getErrors()), "line", i + 1))
          .build();
      }

      // Check for a live (non-retired) template with the same name + kind.
      // If one exists, copy-on-write — retire the prior, mint the next version.
      Optional<ShepardTemplate> existing = dao.findLatestByName(entry.getName(), entry.getTemplateKind());

      ShepardTemplate toSave;
      if (existing.isPresent()) {
        // Retire the prior row first.
        ShepardTemplate prior = existing.get();
        prior.setRetired(true);
        prior.setUpdatedAt(now);
        dao.createOrUpdate(prior);

        // Mint the next version.
        toSave = dao.nextVersionOf(prior);
      } else {
        toSave = new ShepardTemplate(entry.getName(), entry.getTemplateKind(), entry.getBody());
      }

      // Populate from the import entry (caller/timestamps are always the importer + now).
      toSave.setBody(entry.getBody());
      toSave.setDescription(entry.getDescription());
      toSave.setTags(entry.getTags() != null ? entry.getTags() : new ArrayList<>());
      toSave.setCreatedBy(caller);
      toSave.setCreatedAt(now);
      toSave.setUpdatedAt(now);

      ShepardTemplate saved = dao.createOrUpdate(toSave);
      created.add(ShepardTemplateIO.from(saved));
    }

    return Response.ok(created).build();
  }

  // -----------------------------------------------------------------------
  // Wire shape for the YAML document
  // -----------------------------------------------------------------------

  /**
   * One entry in the YAML export / import document. Carries the
   * portable fields only — {@code appId}, {@code createdBy},
   * {@code createdAt}, {@code updatedAt}, and {@code retired} are
   * deliberately excluded because import always mints new entities.
   *
   * <p>{@code version} is included in the export as informational
   * metadata so a human reader can see the version of the exported
   * template. On import it is ignored — the new entity always starts
   * at version 1 (or copy-on-write-incremented from an existing live
   * template with the same name + kind). The field is nullable on
   * deserialization ({@link com.fasterxml.jackson.annotation.JsonIgnoreProperties}
   * is not needed — Jackson quietly ignores extra fields in the
   * default mapper; but version, being present in the export
   * document, must be a declared field so the round-trip YAML is
   * valid YAML with no unknown-property warnings).
   */
  public static class TemplateExportEntry {

    private String name;
    private String templateKind;
    /** Informational — present in the export document; ignored on import. */
    private Integer version;
    private String description;
    private List<String> tags;
    private String body;

    public TemplateExportEntry() {}

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getTemplateKind() {
      return templateKind;
    }

    public void setTemplateKind(String templateKind) {
      this.templateKind = templateKind;
    }

    public Integer getVersion() {
      return version;
    }

    public void setVersion(Integer version) {
      this.version = version;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public List<String> getTags() {
      return tags;
    }

    public void setTags(List<String> tags) {
      this.tags = tags;
    }

    public String getBody() {
      return body;
    }

    public void setBody(String body) {
      this.body = body;
    }

    static TemplateExportEntry from(ShepardTemplate t) {
      TemplateExportEntry e = new TemplateExportEntry();
      e.setName(t.getName());
      e.setTemplateKind(t.getTemplateKind());
      e.setVersion(t.getVersion());
      e.setDescription(t.getDescription());
      e.setTags(t.getTags());
      e.setBody(t.getBody());
      return e;
    }
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private static ObjectMapper buildYamlMapper() {
    YAMLFactory factory = YAMLFactory.builder()
      .disable(YAMLGenerator.Feature.SPLIT_LINES)
      .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
      .build();
    return new ObjectMapper(factory);
  }
}
