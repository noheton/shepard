package de.dlr.shepard.search.unified;

import de.dlr.shepard.search.QueryValidator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class Searcher {

  private StructuredDataSearcher structuredDataSearcher;
  private CollectionSearcher collectionSearcher;
  private DataObjectSearcher dataObjectSearcher;
  private ReferenceSearcher referenceSearcher;

  Searcher() {}

  @Inject
  public Searcher(
    StructuredDataSearcher structuredDataSearcher,
    CollectionSearcher collectionSearcher,
    DataObjectSearcher dataObjectSearcher,
    ReferenceSearcher referenceSearcher
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
