package de.dlr.shepard.v2.shapes.resources;

import de.dlr.shepard.v2.shapes.builder.ShaclShapeBuilder;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import de.dlr.shepard.v2.shapes.io.ShapeBuildRequestIO;
import de.dlr.shepard.v2.shapes.io.ShapeBuildResponseIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code POST /v2/shapes/build} — compile a JSON DSL into canonical SHACL
 * Turtle (V2CONV-B6).
 *
 * <p><b>What it does.</b> Takes the {@link ShapeBuildRequestIO} JSON DSL — the
 * exact shape the visual template editor emits — and returns the deterministic
 * Turtle produced by {@link ShaclShapeBuilder}. This is the live-preview seam:
 * the editor serialises its rows to the DSL on every change and renders the
 * returned {@code shapeGraph} so the author sees the SHACL they are composing
 * without hand-writing Turtle. The same {@code shapeGraph} is then stored on the
 * {@code ShepardTemplate} ({@code body.shapeGraph}) and drives validation,
 * create-form generation, and rendering — the "author once, used four ways"
 * model from {@code aidocs/platform/191-v2-surface-convergence.md §3}.
 *
 * <p><b>Why a thin endpoint.</b> {@link ShaclShapeBuilder} is a pure,
 * deterministic compiler that already existed (B1). This endpoint is the
 * minimal REST surface that exposes it so the frontend (and MCP tools / plugin
 * authors) can round-trip the DSL. It reads no stored data and writes nothing —
 * compilation is a pure function of the request body.
 *
 * <p><b>Access.</b> Any authenticated user. Template <em>persistence</em> is
 * still gated at the {@code instance-admin} level on {@code POST /v2/templates};
 * this endpoint only previews, so it carries the looser gate matching the
 * sibling {@link ShapesValidateRest}.
 *
 * <p><b>Status codes.</b>
 * <ul>
 *   <li>{@code 200} — the DSL compiled; {@code shapeGraph} carries the Turtle.</li>
 *   <li>{@code 400} — the request body was missing, or the DSL was structurally
 *       invalid (e.g. a property with a blank {@code sh:path}). The
 *       {@code error} field explains why.</li>
 *   <li>{@code 401} — not authenticated.</li>
 * </ul>
 *
 * <p><b>Cross-references.</b>
 * <ul>
 *   <li>{@link ShapesValidateRest} — sibling validate endpoint (round-trips the
 *       compiled shape against a candidate data graph).</li>
 *   <li>{@link ShaclShapeBuilder} — the compiler this endpoint wraps.</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/shapes")
@RequestScoped
@Tag(name = "Shapes")
public class ShapesBuildRest {

  @Inject
  ShaclShapeBuilder builder;

  @POST
  @Path("/build")
  @RolesAllowed("authenticated")
  @Operation(
    summary = "Compile a JSON DSL into canonical SHACL Turtle.",
    description = "Stateless, read-only. The visual template editor emits the JSON DSL; this returns " +
    "the deterministic shape graph the same author then stores on a ShepardTemplate. A structurally " +
    "invalid DSL (blank predicate path, etc.) is a 400 with the reason in the error field."
  )
  @APIResponse(
    responseCode = "200",
    description = "Compiled shape graph. Inspect shapeGraph + shapeIri.",
    content = @Content(schema = @Schema(implementation = ShapeBuildResponseIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Request body missing, or the DSL was structurally invalid.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ShapeBuildResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response build(
    @RequestBody(
      required = true,
      description = "The shape JSON DSL the editor emits.",
      content = @Content(schema = @Schema(implementation = ShapeBuildRequestIO.class))
    )
    ShapeBuildRequestIO body
  ) {
    if (body == null) {
      return Response.status(Response.Status.BAD_REQUEST)
        .type("application/problem+json")
        .entity(ShapeBuildResponseIO.invalid("request body required"))
        .build();
    }
    try {
      ShapeSpec spec = body.toSpec();
      String turtle = builder.toTurtle(spec);
      return Response.ok(ShapeBuildResponseIO.ok(resolveShapeIri(body.shapeIri()), turtle)).build();
    } catch (IllegalArgumentException ex) {
      // Structural DSL problem the builder rejected — surface the reason
      // so the editor can highlight the offending row inline.
      Log.debugf("ShapesBuildRest: invalid DSL (%s).", ex.getMessage());
      return Response.status(Response.Status.BAD_REQUEST)
        .type("application/problem+json")
        .entity(ShapeBuildResponseIO.invalid(ex.getMessage()))
        .build();
    }
  }

  /**
   * Echo the IRI the builder will have used: the request's IRI when present,
   * else the builder's deterministic anonymous-shape IRI. Mirrors the resolution
   * inside {@link ShaclShapeBuilder#toTurtle} so the editor displays the same
   * IRI it sees in the Turtle.
   */
  private static String resolveShapeIri(String requested) {
    return (requested == null || requested.isBlank())
      ? ShaclShapeBuilder.DEFAULT_SHAPE_IRI
      : requested.strip();
  }
}
