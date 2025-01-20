package de.dlr.shepard.common.search.container;

import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.common.search.ASearchResults;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContainerSearchResult extends ASearchResults<ContainerSearchParams> {

  private BasicContainerIO[] results;
  private Integer totalResults;

  public ContainerSearchResult(BasicContainerIO[] results, ContainerSearchParams searchParams, Integer total) {
    super(searchParams);
    this.results = results;
    this.totalResults = total;
  }
}
