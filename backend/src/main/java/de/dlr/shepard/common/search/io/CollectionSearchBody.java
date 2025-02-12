package de.dlr.shepard.common.search.io;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CollectionSearchBody extends ASearchBody<CollectionSearchParams> {

  public CollectionSearchBody(CollectionSearchParams searchParams) {
    super(searchParams);
  }

  public CollectionSearchBody(SearchBody searchBody) {
    super(new CollectionSearchParams(searchBody.getSearchParams().getQuery()));
  }
}
