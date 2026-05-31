package de.dlr.shepard.common.neo4j.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import java.util.Arrays;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "BasicContainer")
public class BasicContainerIO extends BasicEntityIO {

  @Schema(readOnly = true, required = true)
  private ContainerType type;

  /**
   * #27-ARCHIVED — lifecycle status mirroring
   * {@link de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO#status}.
   * Null in the wire representation means "not yet declared; treated as READY
   * by the {@code ArchiveStateGuard}". Omitted from the wire when null
   * ({@code @JsonInclude(NON_NULL)}) so the legacy {@code /shepard/api/}
   * surface stays byte-identical to upstream v5.2.0 (which has no such field).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, enumeration = {"DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"}, example = "READY")
  private String status;

  public BasicContainerIO(BasicContainer container) {
    super(container);
    type = Arrays.stream(ContainerType.values())
      .filter(containerType -> containerType.getTypeName().equals(container.getClass().getSimpleName()))
      .findFirst()
      .orElse(ContainerType.BASIC);
    this.status = container.getStatus();
  }
}
