package de.dlr.shepard.v2.users.resources;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserAvatarService;
import de.dlr.shepard.auth.users.services.UserService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.bson.Document;
import org.bson.types.Binary;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * U1e — avatar upload/delete/render endpoints.
 *
 * <ul>
 *   <li>{@code PUT    /v2/users/me/avatar}        — upload or replace avatar (multipart)</li>
 *   <li>{@code DELETE /v2/users/me/avatar}        — remove avatar</li>
 *   <li>{@code GET    /v2/users/{appId}/avatar}   — public render (no auth required)</li>
 * </ul>
 *
 * <p>Avatars are stored in MongoDB collection {@code userAvatars} keyed by {@code userAppId}.
 * Max size: {@value UserAvatarService#MAX_BYTES} bytes (2 MB).
 * Allowed types: JPEG, PNG, GIF, WebP.
 */
@Path("/v2")
@RequestScoped
@Tag(name = "Me")
public class UserAvatarRest {

  @Inject
  UserAvatarService avatarService;

  @Inject
  UserService userService;

  // ── PUT /v2/users/me/avatar ───────────────────────────────────────────────

  @PUT
  @Path("/users/me/avatar")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Upload or replace the caller's avatar (max 2 MB; JPEG/PNG/GIF/WebP).")
  @APIResponse(responseCode = "204", description = "Avatar stored.")
  @APIResponse(responseCode = "400", description = "Missing file part, unsupported type, or file exceeds 2 MB.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response putAvatar(
      @RestForm("file") FileUpload upload,
      @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (upload == null || upload.uploadedFile() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("file part is required").build();
    }

    String contentType = upload.contentType();
    String mimeType = (contentType != null && contentType.contains(";"))
        ? contentType.split(";")[0].trim()
        : contentType;
    if (mimeType == null || !UserAvatarService.ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("unsupported MIME type — use image/jpeg, image/png, image/gif, or image/webp")
          .build();
    }

    User caller = userService.getCurrentUser();
    if (caller == null || caller.getAppId() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    File uploadedFile = upload.uploadedFile().toFile();
    try (InputStream is = new FileInputStream(uploadedFile)) {
      boolean ok = avatarService.upsert(caller.getAppId(), mimeType, is);
      if (!ok) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("avatar exceeds maximum size of 2 MB")
            .build();
      }
    } catch (IOException e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("failed to read uploaded file")
          .build();
    }

    return Response.noContent().build();
  }

  // ── DELETE /v2/users/me/avatar ────────────────────────────────────────────

  @DELETE
  @Path("/users/me/avatar")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Remove the caller's avatar.")
  @APIResponse(responseCode = "204", description = "Avatar removed (or was already absent).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response deleteAvatar(@Context SecurityContext securityContext) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    User caller = userService.getCurrentUser();
    if (caller == null || caller.getAppId() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    avatarService.delete(caller.getAppId());
    return Response.noContent().build();
  }

  // ── GET /v2/users/{appId}/avatar ──────────────────────────────────────────

  @GET
  @Path("/users/{appId}/avatar")
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
