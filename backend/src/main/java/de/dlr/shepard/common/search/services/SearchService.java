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

  private StructuredDataSearchService structuredDataSearcher;
  private CollectionSearchService collectionSearcher;
  private DataObjectSearchService dataObjectSearcher;
  private ReferenceSearchService referenceSearcher;

  SearchService() {}

  @Inject
  public SearchService(
    StructuredDataSearchService structuredDataSearcher,
    CollectionSearchService collectionSearcher,
    DataObjectSearchService dataObjectSearcher,
    ReferenceSearchService referenceSearcher
  ) {
    this.structuredDataSearcher = structuredDataSearcher;
    this.collectionSearcher = collectionSearcher;
    this.dataObjectSearcher = dataObjectSearcher;
    this.referenceSearcher = referenceSearcher;
  }

  public ResponseBody search(SearchBody searchBody, String userName) {
    QueryValidator.checkQuery(searchBody.getSearchParams().getQuery());
    ResponseBody ret =
      switch (searchBody.getSearchParams().getQueryType()) {
        case StructuredData -> structuredDataSearcher.search(searchBody, userName);
        case Collection -> searchCollections(searchBody, userName);
        case DataObject -> dataObjectSearcher.search(searchBody, userName);
        case Reference -> referenceSearcher.search(searchBody, userName);
        default -> null;
      };
    return ret;
  }

  private ResponseBody searchCollections(SearchBody searchBody, String userName) {
    PaginatedCollectionList paginatedCollectionList = collectionSearcher.search(
      searchBody.getSearchParams().getQuery(),
      userName,
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
