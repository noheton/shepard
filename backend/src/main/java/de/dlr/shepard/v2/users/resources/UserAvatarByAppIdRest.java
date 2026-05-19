package de.dlr.shepard.v2.users.resources;

import de.dlr.shepard.auth.users.services.UserAvatarService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.bson.Document;
import org.bson.types.Binary;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * U1e — public GET of another user's avatar by appId. Split out from
 * {@link UserAvatarRest} so each class has a single, unambiguous
 * class-level {@code @Path}, avoiding the JAX-RS resource-selection
 * conflict with {@code MeRest}'s {@code @Path("/v2/users/me")}.
 *
 * <p>No authentication: rendering an avatar in the UI must work for
 * any user the page is showing (e.g. {@code createdBy} columns,
 * future avatars in the contributor sparklines on the landing page).
 */
@Path("/v2/users/{appId}/avatar")
@RequestScoped
@Tag(name = "Me")
public class UserAvatarByAppIdRest {

  @Inject
  UserAvatarService avatarService;

  @GET
  @Produces("image/*")
  @Operation(summary = "Get a user's avatar. No authentication required; returns 404 if none uploaded.")
  @APIResponse(responseCode = "200", description = "Avatar bytes.")
  @APIResponse(responseCode = "404", description = "No avatar for this user.")
  public Response getAvatar(@PathParam("appId") String appId) {
    Document doc = avatarService.find(appId);
    if (doc == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    Binary binary = doc.get("data", Binary.class);
    String mimeType = doc.getString("mimeType");
    if (binary == null || mimeType == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    byte[] data = binary.getData();
    StreamingOutput stream = out -> out.write(data);

    return Response.ok(stream, mimeType)
        .header("Cache-Control", "max-age=3600, must-revalidate")
        .build();
  }
}
