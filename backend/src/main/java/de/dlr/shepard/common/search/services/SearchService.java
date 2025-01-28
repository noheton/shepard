package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.query.QueryValidator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

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
        case Collection -> collectionSearcher.search(searchBody, userName);
        case DataObject -> dataObjectSearcher.search(searchBody, userName);
        case Reference -> referenceSearcher.search(searchBody, userName);
        default -> null;
      };
    return ret;
  }
}
