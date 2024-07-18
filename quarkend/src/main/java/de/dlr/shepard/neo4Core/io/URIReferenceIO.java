package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.URIReference;
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
  private String uri;

  public URIReferenceIO(URIReference ref) {
    super(ref);
    this.uri = ref.getUri();
  }
}
