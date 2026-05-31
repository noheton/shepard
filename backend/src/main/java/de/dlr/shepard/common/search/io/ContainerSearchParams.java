package de.dlr.shepard.common.search.io;

import de.dlr.shepard.common.neo4j.entities.ContainerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContainerSearchParams extends ASearchParams {

  @Valid
  @NotNull
  private ContainerType queryType;

  private String createdBy;

  public ContainerSearchParams(String query, ContainerType queryType) {
    super(query);
    this.queryType = queryType;
  }

  public ContainerSearchParams(String query, ContainerType queryType, String createdBy) {
    super(query);
    this.queryType = queryType;
    this.createdBy = createdBy;
  }
}
