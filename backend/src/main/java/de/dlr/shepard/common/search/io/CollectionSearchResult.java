package de.dlr.shepard.common.search.io;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.context.collection.io.CollectionIO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CollectionSearchResult extends ASearchResults<CollectionSearchParams> {

  @Schema(required = true)
  private CollectionIO[] results;

  @Schema(required = true)
  private Integer totalResults;

  public CollectionSearchResult(CollectionIO[] results, CollectionSearchParams searchParams, Integer total) {
    super(searchParams);
    this.results = results;
    this.totalResults = total;
  }

  public ResponseBody toResponseBody() {
    ResultTriple[] resultTriples = new ResultTriple[this.results.length];
    BasicEntityIO[] results = new BasicEntityIO[this.results.length];
    for (int i = 0; i < this.results.length; i++) {
      resultTriples[i] = new ResultTriple(this.results[i].getId());
      results[i] = new BasicEntityIO(this.results[i]);
    }
    ResponseBody responseBody = new ResponseBody(
      resultTriples,
      results,
      new SearchParams(this.getSearchParams().getQuery(), QueryType.Collection)
    );
    return responseBody;
  }
}
