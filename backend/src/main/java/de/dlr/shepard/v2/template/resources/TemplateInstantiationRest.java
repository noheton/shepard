package de.dlr.shepard.v2.template.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.template.io.TemplateInstantiateRequestIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code POST /v2/collections/{collectionAppId}/data-objects/from-template/{templateAppId}}
 *
 * <p>Server-side DataObject instantiation from a {@code :ShepardTemplate} per T1e
 * ({@code aidocs/16-dispatcher-backlog.md §T1e}).
 *
 * <p>Steps:
 * <ol>
 *   <li>401 when the caller is unauthenticated.</li>
 *   <li>404 when the Collection is not found.</li>
 *   <li>403 when the caller lacks Write on the Collection.</li>
 *   <li>404 when the template is not found.</li>
 *   <li>409 when the template is retired.</li>
 *   <li>403 when the Collection has a non-empty {@code :ALLOWS_TEMPLATE} allow-list
 *       and the template is not in it (empty list = unrestricted).</li>
 *   <li>Create the DataObject with attributes extracted from the template body
 *       ({@code dataobjects[0].attributes}).</li>
 *   <li>Record the {@code :CREATED_FROM_TEMPLATE} relationship.</li>
 *   <li>Return 201 + the new {@link DataObjectIO}.</li>
 * </ol>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{collectionAppId}/data-objects/from-template")
@RequestScoped
@Tag(name = "Collection templates (v2)")
public class TemplateInstantiationRest {

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  CollectionPropertiesDAO collectionPropsDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  ObjectMapper objectMapper;

  @POST
  @Path("/{templateAppId}")
  @Operation(
    summary = "Create a DataObject from a ShepardTemplate (server-side instantiation).",
    description = "Parses the template body's `dataobjects[0].attributes` and applies them to the new " +
    "DataObject. Records a `:CREATED_FROM_TEMPLATE` relationship back to the template. " +
    "Requires Write permission on the Collection. The template must be non-retired; " +
    "if the Collection has a non-empty allow-list the template must appear in it."
  )
  @APIResponse(
    responseCode = "201",
    description = "DataObject created from the template.",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection, or the template is not in the Collection's allow-list.")
  @APIResponse(responseCode = "404", description = "Collection or template not found.")
  @APIResponse(responseCode = "409", description = "Template is retired and cannot be used for new instantiations.")
  public Response instantiateDataObject(
    @PathParam("collectionAppId") String collectionAppId,
    @PathParam("templateAppId") String templateAppId,
    @RequestBody(
      required = false,
      description = "Optional override for the DataObject name. All fields are optional.",
      content = @Content(schema = @Schema(implementation = TemplateInstantiateRequestIO.class))
    ) TemplateInstantiateRequestIO body,
    @Context SecurityContext securityContext
  ) {
    // Step 1: authentication
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // Step 2 + 3: resolve collection and check Write permission
    Optional<Long> ogmId = collectionPropsDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("No Collection with appId " + collectionAppId)
        .build();
    }
    String caller = securityContext.getUserPrincipal().getName();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Write, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN)
        .entity("Caller lacks Write on Collection " + collectionAppId)
        .build();
    }

    // Step 4: resolve template
    Optional<ShepardTemplate> templateOpt = templateDAO.findByAppId(templateAppId);
    if (templateOpt.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("No template with appId " + templateAppId)
        .build();
    }
    ShepardTemplate template = templateOpt.get();

    // Step 5: 409 if retired
    if (template.isRetired()) {
      return Response.status(Response.Status.CONFLICT)
        .entity("Template " + templateAppId + " is retired; pick a non-retired version")
        .build();
    }

    // Step 6: allow-list guard (empty list = unrestricted)
    List<ShepardTemplate> allowed = templateDAO.listAllowedForCollection(collectionAppId);
    if (!allowed.isEmpty()) {
      boolean inList = allowed.stream().anyMatch(t -> templateAppId.equals(t.getAppId()));
      if (!inList) {
        return Response.status(Response.Status.FORBIDDEN)
          .entity("Template " + templateAppId + " is not in the allow-list for Collection " + collectionAppId)
          .build();
      }
    }

    // Step 7: resolve DataObject name from: request body → template body → fallback
    String dataObjectName = resolveDataObjectName(body, template);

    // Step 8: extract attributes from template body dataobjects[0].attributes
    Map<String, String> attributes = extractAttributes(template.getBody());

    // Step 9: create the DataObject
    DataObjectIO doIO = new DataObjectIO();
    doIO.setName(dataObjectName);
    doIO.setAttributes(attributes.isEmpty() ? null : attributes);

    DataObject created = dataObjectService.createDataObject(ogmId.get(), doIO);

    // Step 10: record :CREATED_FROM_TEMPLATE edge
    templateDAO.recordCreatedFrom(created.getShepardId(), template);

    return Response.status(Response.Status.CREATED).entity(new DataObjectIO(created)).build();
  }

  /**
   * Resolve the DataObject name in priority order:
   * <ol>
   *   <li>Request body {@code name} (non-null, non-blank).</li>
   *   <li>{@code dataobjects[0].name} from the template body.</li>
   *   <li>Template {@code name} + {@code "-"} + current millis.</li>
   * </ol>
   */
  private String resolveDataObjectName(TemplateInstantiateRequestIO body, ShepardTemplate template) {
    if (body != null && body.getName() != null && !body.getName().isBlank()) {
      return body.getName();
    }
    // Try template body dataobjects[0].name
    if (template.getBody() != null) {
      try {
        JsonNode root = objectMapper.readTree(template.getBody());
        JsonNode firstDo = root.path("dataobjects").path(0);
        JsonNode nameNode = firstDo.path("name");
        if (!nameNode.isMissingNode() && !nameNode.isNull() && nameNode.isTextual() && !nameNode.textValue().isBlank()) {
          return nameNode.textValue();
        }
      } catch (JsonProcessingException e) {
        Log.warnf("Could not parse template body for DataObject name fallback (templateAppId=%s): %s", template.getAppId(), e.getMessage());
      }
    }
    return template.getName() + "-" + System.currentTimeMillis();
  }

  /**
   * Extract {@code dataobjects[0].attributes} from the template body as a
   * {@code Map<String,String>}. Returns an empty map when the path is absent,
   * the body is null, or parsing fails. Tolerates missing keys gracefully.
   */
  private Map<String, String> extractAttributes(String body) {
    if (body == null || body.isBlank()) return Map.of();
    JsonNode root;
    try {
      root = objectMapper.readTree(body);
    } catch (JsonProcessingException e) {
      Log.warnf("Could not parse template body for attribute extraction: %s", e.getMessage());
      return Map.of();
    }
    JsonNode attrs = root.path("dataobjects").path(0).path("attributes");
    if (attrs.isMissingNode() || attrs.isNull() || !attrs.isObject()) return Map.of();

    Map<String, String> result = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = attrs.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode v = entry.getValue();
      if (v != null && !v.isNull()) {
        result.put(entry.getKey(), v.isTextual() ? v.textValue() : v.toString());
      }
    }
    return result;
  }
}
