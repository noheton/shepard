package de.dlr.shepard.v2.shapes.resources;

import de.dlr.shepard.v2.shapes.io.ShapeValidationReportIO;
import de.dlr.shepard.v2.shapes.io.ShapeValidationRequestIO;
import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
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
 * {@code POST /v2/shapes/validate} — pre-flight SHACL validation of a
 * candidate RDF payload against a candidate shape graph.
 *
 * <p><b>Why validate-only.</b> Per the SHACL changeover brief
 * (2026-05-22, decision #3), this endpoint does not maintain a shape
 * catalog — Template ({@code /v2/templates?kind=view}) carries that.
 * The use cases this endpoint serves:
 *
 * <ul>
 *   <li><b>MCP tool</b> — an LLM agent constructing a candidate
 *       JSON-LD/Turtle payload calls this to know whether the
 *       payload would satisfy the shape <em>before</em> attempting a
 *       write that would otherwise round-trip a 422. Per Digital
 *       Native review #4.</li>
 *   <li><b>UI form-builder</b> — a Vue form-builder validating
 *       partial payloads at typing time without poking a write
 *       endpoint.</li>
 *   <li><b>Plugin authors</b> — quick sanity check on shape edits
 *       without a separate command-line tool.</li>
 * </ul>
 *
 * <p><b>Access.</b> Any authenticated user. The endpoint reads no
 * stored data and writes nothing — validation is a pure function of
 * the two inputs.
 *
 * <p><b>Status codes.</b>
 * <ul>
 *   <li>{@code 200} — validation ran. The body's {@code conforms}
 *       field tells you whether the data satisfied the shape. A
 *       payload that parsed but didn't conform is still a 200; the
 *       findings explain why. The MCP tool depends on this.</li>
 *   <li>{@code 400} — request body was missing or malformed JSON.
 *       Bad Turtle in the {@code dataTurtle} or {@code shapeTurtle}
 *       fields is NOT a 400; it returns a 200 with
 *       {@code parseError} set, so callers can branch on it.</li>
 *   <li>{@code 401} — no authenticated user.</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/shapes")
@RequestScoped
@Tag(name = "Shapes (v2)")
public class ShapesValidateRest {

  @Inject
  JenaShaclValidator validator;

  @POST
  @Path("/validate")
  @RolesAllowed("authenticated")
  @Operation(
    summary = "Validate a candidate RDF payload against a SHACL shape graph.",
    description = "Stateless, read-only. Returns the SHACL validation report. Bad Turtle in either input " +
    "is reported via parseError in the 200 body, NOT as a 400 — so a caller (e.g. an MCP tool) can " +
    "distinguish 'malformed payload' from 'malformed request'."
  )
  @APIResponse(
    responseCode = "200",
    description = "Validation report. Inspect conforms + findings + parseError.",
    content = @Content(schema = @Schema(implementation = ShapeValidationReportIO.class))
  )
  @APIResponse(responseCode = "400", description = "Request body missing or not valid JSON.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response validate(
    @RequestBody(
      required = true,
      description = "Candidate data + shape graphs, Turtle-serialised.",
      content = @Content(schema = @Schema(implementation = ShapeValidationRequestIO.class))
    )
    ShapeValidationRequestIO body
  ) {
    if (body == null) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(ShapeValidationReportIO.from(
          JenaShaclValidator.Report.parseError("request body required")
        ))
        .build();
    }
    JenaShaclValidator.Report report = validator.validate(body.dataTurtle(), body.shapeTurtle());
    return Response.ok(ShapeValidationReportIO.from(report)).build();
  }
}
