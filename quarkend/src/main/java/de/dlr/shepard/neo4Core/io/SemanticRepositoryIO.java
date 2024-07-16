package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.semantics.SemanticRepositoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "SemanticRepository")
public class SemanticRepositoryIO extends BasicEntityIO {

  @NotNull
  private SemanticRepositoryType type;

  @NotBlank
  private String endpoint;

  public SemanticRepositoryIO(SemanticRepository container) {
    super(container);
    this.type = container.getType();
    this.endpoint = container.getEndpoint();
  }
}
