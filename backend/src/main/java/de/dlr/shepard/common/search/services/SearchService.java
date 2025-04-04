package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.common.search.io.CollectionSearchBody;
import de.dlr.shepard.common.search.io.CollectionSearchResult;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.context.collection.io.CollectionIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Optional;

@RequestScoped
public class SearchService {

  @Inject
  StructuredDataSearchService structuredDataSearcher;

  @Inject
  CollectionSearchService collectionSearcher;

  @Inject
  DataObjectSearchService dataObjectSearcher;

  @Inject
  ReferenceSearchService referenceSearcher;

  public ResponseBody search(SearchBody searchBody) {
    QueryValidator.checkQuery(searchBody.getSearchParams().getQuery());
    ResponseBody ret =
      switch (searchBody.getSearchParams().getQueryType()) {
        case StructuredData -> structuredDataSearcher.search(searchBody);
        case Collection -> searchCollections(searchBody);
        case DataObject -> dataObjectSearcher.search(searchBody);
        case Reference -> referenceSearcher.search(searchBody);
        default -> null;
      };
    return ret;
  }

  private ResponseBody searchCollections(SearchBody searchBody) {
    PaginatedCollectionList paginatedCollectionList = collectionSearcher.search(
      searchBody.getSearchParams().getQuery(),
      Optional.empty(),
      Optional.empty(),
      BasicCollectionAttributes.createdAt,
      true
    );

    return new CollectionSearchResult(
      paginatedCollectionList.getResults().stream().map(CollectionIO::new).toList(),
      new CollectionSearchBody(searchBody).getSearchParams(),
      paginatedCollectionList.getTotalResults()
    ).toResponseBody();
  }
}
