package de.dlr.shepard.v2.timeseriescontainer.io;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-AXIS-AUTO — request body for annotating a channel with its spatial axis role.
 *
 * <p>Unlike the general {@link de.dlr.shepard.context.semantic.io.SemanticAnnotationIO},
 * this IO does not require a semantic repository: the axis value is a short well-known
 * token (one of {@code x | y | z | rot_a | rot_b | rot_c}), not an ontology term.
 * The backend writes it as a {@link de.dlr.shepard.context.semantic.entities.SemanticAnnotation}
 * with {@code propertyIRI = Constants.TS_AXIS_PREDICATE} and {@code valueIRI = value},
 * both repository pointers left null.
 */
@Schema(description = "Assign a spatial axis role to a timeseries channel (TS-AXIS-AUTO).")
public record ChannelAxisAnnotationIO(

  @Schema(
    description = "Axis role to assign to this channel. " +
      "Accepted values: x, y, z, rot_a, rot_b, rot_c.",
    example = "x",
    required = true
  )
  @NotBlank
  @Pattern(regexp = "^(x|y|z|rot_a|rot_b|rot_c)$",
           message = "value must be one of: x, y, z, rot_a, rot_b, rot_c")
  String value

) {}
