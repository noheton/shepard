package de.dlr.shepard.common.search.unified;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.ASearchResults;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ResponseBody extends ASearchResults<SearchParams> {

  private ResultTriple[] resultSet;
  private BasicEntityIO[] results;

  public ResponseBody(ResultTriple[] resultSet, BasicEntityIO[] results, SearchParams searchParams) {
    super(searchParams);
    this.resultSet = resultSet;
    this.results = results;
  }
}
