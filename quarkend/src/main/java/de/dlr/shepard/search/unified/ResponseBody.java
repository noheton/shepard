package de.dlr.shepard.search.unified;

import de.dlr.shepard.neo4Core.io.VersionableEntityIO;
import de.dlr.shepard.search.ASearchResults;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ResponseBody extends ASearchResults<SearchParams> {

  private ResultTriple[] resultSet;
  private VersionableEntityIO[] results;

  public ResponseBody(ResultTriple[] resultSet, VersionableEntityIO[] results, SearchParams searchParams) {
    super(searchParams);
    this.resultSet = resultSet;
    this.results = results;
  }
}
