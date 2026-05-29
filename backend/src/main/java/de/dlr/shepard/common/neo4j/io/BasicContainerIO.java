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
   * Lifecycle status of this container. Nullable; null means "not yet set".
   *
   * <p>Allowed values: {@code DRAFT}, {@code IN_REVIEW}, {@code READY},
   * {@code PUBLISHED}, {@code ARCHIVED}. {@code ARCHIVED} was introduced in
   * backlog row #27-ARCHIVED-01; status transition guards are deferred to
   * #27-ARCHIVED-02.
   *
   * <p>Absent from the wire when null ({@code @JsonInclude(NON_NULL)}) for
   * forward-compatibility — clients that only know the pre-#27-ARCHIVED-01
   * schema see no change until an operator sets a value.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    enumeration = { "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED" }
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
