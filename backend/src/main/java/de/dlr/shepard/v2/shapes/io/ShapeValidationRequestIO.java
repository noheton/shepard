package de.dlr.shepard.v2.shapes.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/shapes/validate}.
 *
 * <p>Both fields carry RDF in Turtle serialisation. The validator
 * parses them with Jena RIOT in-process — see
 * {@link de.dlr.shepard.v2.shapes.validator.JenaShaclValidator}.
 *
 * <p>Why a request body and not a query string: shape graphs and
 * candidate data graphs are routinely multi-kilobyte (the
 * {@code mffd-ncr.shacl.ttl} file is ~10 KB), well beyond what
 * fits comfortably in a URL.
 */
@Schema(
  description = "Candidate data + shape graphs (Turtle) for SHACL validation. Both fields required."
)
public record ShapeValidationRequestIO(
  @Schema(
    description = "Candidate data graph as Turtle (UTF-8). The graph the validator checks.",
    example = "@prefix ex: <http://example.org/> .\nex:Alice a ex:Person ."
  )
  String dataTurtle,
  @Schema(
    description = "Shape graph as Turtle (UTF-8). SHACL NodeShapes / PropertyShapes the data must satisfy.",
    example = "@prefix sh: <http://www.w3.org/ns/shacl#> ."
  )
  String shapeTurtle
) {}
