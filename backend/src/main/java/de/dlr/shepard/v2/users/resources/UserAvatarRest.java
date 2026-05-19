package de.dlr.shepard.v2.users.resources;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserAvatarService;
import de.dlr.shepard.auth.users.services.UserService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * U1e — caller's avatar (PUT + DELETE).
 *
 * <p>Routing note: this resource's class-level {@code @Path} is the FULL
 * path. Previously the file mixed class-level {@code @Path("/v2/users")}
 * with method-level {@code @Path("/me/avatar")}, but {@link MeRest}
 * already claims {@code /v2/users/me} — JAX-RS picks the most-specific
 * matching resource class, and {@code MeRest} won, leaving the
 * `/avatar` sub-path with no matching method (404 "Unable to find
 * matching target resource method"). Putting the full path on the
 * class makes the route resolution unambiguous; the GET-by-appId
 * path lives in its sibling {@link UserAvatarByAppIdRest}.
 *
 * <p>Avatars are stored in MongoDB collection {@code userAvatars} keyed
 * by {@code userAppId}. Max size: {@value UserAvatarService#MAX_BYTES}
 * bytes (2 MB). Allowed types: JPEG, PNG, GIF, WebP.
 */
@Path("/v2/users/me/avatar")
@RequestScoped
@Tag(name = "Me")
public class UserAvatarRest {

  @Inject
  UserAvatarService avatarService;

  @Inject
  UserService userService;

  @PUT
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

  @DELETE
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
}
