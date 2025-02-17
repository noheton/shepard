package de.dlr.shepard.common.search.io;

import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContainerSearchResult extends ASearchResults<ContainerSearchParams> {

  @Schema(required = true)
  private List<BasicContainerIO> results;

  @Schema(required = true)
  private Integer totalResults;

  public ContainerSearchResult(List<BasicContainerIO> results, ContainerSearchParams searchParams, Integer total) {
    super(searchParams);
    this.results = results;
    this.totalResults = total;
  }
}
