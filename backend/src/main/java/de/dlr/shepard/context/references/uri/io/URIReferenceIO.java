package de.dlr.shepard.context.references.uri.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "URIReference")
public class URIReferenceIO extends BasicReferenceIO {

  @NotBlank
  @Schema(required = true)
  private String uri;

  @Schema(nullable = true)
  private String relationship;

  public URIReferenceIO(URIReference ref) {
    super(ref);
    this.uri = ref.getUri();
    this.relationship = ref.getRelationship();
  }
}
