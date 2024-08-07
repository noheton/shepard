package de.dlr.shepard.search.container;

import de.dlr.shepard.neo4Core.io.BasicContainerIO;
import de.dlr.shepard.search.ASearchResults;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContainerSearchResult extends ASearchResults<ContainerSearchParams> {

  private BasicContainerIO[] results;

  public ContainerSearchResult(BasicContainerIO[] results, ContainerSearchParams searchParams) {
    super(searchParams);
    this.results = results;
  }
}
