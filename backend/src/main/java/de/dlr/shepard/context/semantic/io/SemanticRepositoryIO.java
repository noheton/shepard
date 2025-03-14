package de.dlr.shepard.context.semantic.io;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "SemanticRepository")
public class SemanticRepositoryIO extends BasicEntityIO {

  @NotNull
  @Schema(required = true)
  private SemanticRepositoryType type;

  @NotBlank
  @Schema(required = true)
  private String endpoint;

  public SemanticRepositoryIO(SemanticRepository container) {
    super(container);
    this.type = container.getType();
    this.endpoint = container.getEndpoint();
  }
}
