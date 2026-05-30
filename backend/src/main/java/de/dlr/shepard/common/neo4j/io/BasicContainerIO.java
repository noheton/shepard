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
   * #27-CONTAINER-STATUS-01 — container lifecycle status.
   *
   * <p>Nullable (absent from the wire when null so upstream clients see no change).
   * Valid values: {@code DRAFT}, {@code IN_REVIEW}, {@code READY},
   * {@code PUBLISHED}, {@code ARCHIVED}.
   *
   * <p>Transition rules enforced by
   * {@link de.dlr.shepard.common.container.services.ContainerStatusGuard}.
   * Write path wired in #27-ARCHIVED-02.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    enumeration = {"DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"},
    example = "DRAFT"
  )
  private String status;

  public BasicContainerIO(BasicContainer container) {
    super(container);
    type = Arrays.stream(ContainerType.values())
      .filter(containerType -> containerType.getTypeName().equals(container.getClass().getSimpleName()))
      .findFirst()
      .orElse(ContainerType.BASIC);
    status = container.getStatus();
  }
}
