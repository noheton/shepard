package de.dlr.shepard.v2.shapes.io;

import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response body for {@code POST /v2/shapes/validate}.
 *
 * <p>Mirrors the SHACL spec's "validation report" shape but uses
 * plain Java types so the REST layer can serialise without dragging
 * Jena classes onto the wire.
 *
 * <p>{@code parseError} is set when the request payload could not
 * be parsed as Turtle (or could not be parsed as SHACL shapes);
 * {@code engineError} is set when Jena threw during validation —
 * rare. In both cases {@code conforms=false} and {@code findings}
 * is empty.
 */
@Schema(description = "SHACL validation report.")
public record ShapeValidationReportIO(
  @Schema(description = "True iff the data graph satisfies every shape in the shape graph.")
  boolean conforms,

  @Schema(
    description = "Non-null when the Turtle or shape graph could not be parsed.",
    nullable = true
  )
  String parseError,

  @Schema(
    description = "Non-null when the SHACL engine threw during validation.",
    nullable = true
  )
  String engineError,

  @Schema(description = "Per-finding details. Empty when conforms=true.")
  List<FindingIO> findings
) {
  /** Wire-shape adapter for {@link JenaShaclValidator.Report}. */
  public static ShapeValidationReportIO from(JenaShaclValidator.Report r) {
    List<FindingIO> wire = r.findings()
      .stream()
      .map(FindingIO::from)
      .collect(Collectors.toList());
    return new ShapeValidationReportIO(r.conforms(), r.parseError(), r.engineError(), wire);
  }

  /** One SHACL violation. All string fields nullable except {@code severity}. */
  @Schema(description = "One SHACL violation.")
  public record FindingIO(
    @Schema(description = "IRI (or blank-node label) of the node the violation is attached to.", nullable = true)
    String focusNode,
    @Schema(description = "SHACL property path that triggered the violation, stringified. Null for node-level violations.", nullable = true)
    String resultPath,
    @Schema(description = "Offending value (lexical form for literals, IRI for resources).", nullable = true)
    String value,
    @Schema(description = "Severity: Violation | Warning | Info.")
    String severity,
    @Schema(description = "sh:resultMessage where the shape supplies one; empty string otherwise.")
    String message
  ) {
    static FindingIO from(JenaShaclValidator.Finding f) {
      return new FindingIO(f.focusNode(), f.resultPath(), f.value(), f.severity(), f.message());
    }
  }
}
