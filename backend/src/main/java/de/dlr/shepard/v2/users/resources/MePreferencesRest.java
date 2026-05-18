package de.dlr.shepard.v2.users.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.Constants;
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
import jakarta.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code GET/PATCH /v2/users/me/preferences} — per-user UI preference storage
 * per {@code aidocs/16 U1d}.
 *
 * <p>RFC 7396 JSON Merge Patch semantics on PATCH:
 * a null value removes the key; a non-null string value sets the key;
 * keys absent from the body are preserved unchanged.
 *
 * <p>Known UI keys: {@code theme}, {@code language}, {@code timeZone},
 * {@code dateFormat}, {@code defaultPageSize}, {@code defaultLandingPage}.
 * Open-world — additional string keys are accepted without server-side
 * validation. The future U1c {@code SettingDescriptor} layer will add
 * typed validation; this slice stays intentionally permissive.
 *
 * <p>Auth: authenticated user only; no role gate. 401 when unauthenticated.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/users/me/preferences")
@RequestScoped
@Tag(name = "Me")
public class MePreferencesRest {

  @Inject
  UserService userService;

  @GET
  @Operation(
    summary = "Get the caller's UI preferences.",
    description = "Returns the per-user preference map. Returns an empty object `{}` when no " +
    "preferences have been set. Keys are open-world strings; values are always strings."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current preferences map (may be empty).",
    content = @Content(schema = @Schema(implementation = Map.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response getPreferences(@Context SecurityContext sc) {
    if (sc.getUserPrincipal() == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    String username = sc.getUserPrincipal().getName();
    Map<String, String> prefs = userService.getPreferences(username);
    return Response.ok(prefs).build();
  }

  @PATCH
  // Accept both application/merge-patch+json (RFC 7396 preferred) and
  // application/json — the upstream client + the Kiota client default to
  // merge-patch+json for any PATCH; the old listing of just JSON 415'd them.
  // Fix lands with the matching CollectionRest / DataObjectRest pattern.
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Partial-update the caller's UI preferences.",
    description = "RFC 7396 JSON Merge Patch. Keys with non-null string values are set or updated. " +
    "Keys with explicit JSON `null` values are removed. Keys absent from the body are preserved. " +
    "Returns the resulting preference map after the patch is applied. " +
    "Known keys: `theme`, `language`, `timeZone`, `dateFormat`, `defaultPageSize`, `defaultLandingPage`. " +
    "Unknown string keys are accepted (open-world). Non-string non-null values return 400."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated preferences map.",
    content = @Content(schema = @Schema(implementation = Map.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object, or a value is not a string.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response patchPreferences(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(description = "Merge-patch map: string keys, string or null values."))
    ) JsonNode patch,
    @Context SecurityContext sc
  ) {
    if (sc.getUserPrincipal() == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (patch == null || !patch.isObject()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("PATCH body must be a JSON object (RFC 7396 JSON Merge Patch)")
        .build();
    }

    // Build the merge-patch map. Per RFC 7396: null values mean "remove",
    // non-null values must be strings (open-world but no type promotion).
    Map<String, String> patchMap = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      JsonNode value = field.getValue();
      if (value.isNull()) {
        patchMap.put(field.getKey(), null); // null sentinel → remove key
      } else if (value.isTextual()) {
        patchMap.put(field.getKey(), value.asText());
      } else {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity("Value for key '" + field.getKey() + "' must be a string or null")
          .build();
      }
    }

    String username = sc.getUserPrincipal().getName();
    Map<String, String> result = userService.patchPreferences(username, patchMap);
    return Response.ok(result).build();
  }
}
