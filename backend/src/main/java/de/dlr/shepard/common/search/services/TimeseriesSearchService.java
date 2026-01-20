package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.TimeseriesInContainerSearchBody;
import de.dlr.shepard.common.search.io.TimeseriesInContainerSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class TimeseriesSearchService {

  @Inject
  SearchDAO searchDAO;

  @Inject
  UserService userService;

  public TimeseriesInContainerSearchResult search(long containerId, TimeseriesInContainerSearchBody searchBody) {
    User user = userService.getCurrentUser();
    String searchBodyQuery = searchBody.getSearchParams().getQuery();
    String selectionQuery = Neo4jQueryBuilder.annotatableTimeseriesInContainerSelectionQuery(
      containerId,
      searchBodyQuery,
      user.getUsername()
    );
    List<AnnotatableTimeseries> annotatableTimeseriesFound = searchDAO.findAnnotatableTimeseries(
      selectionQuery,
      Constants.ANNOTATABLE_TS_IN_QUERY
    );
    TimeseriesInContainerSearchResult ret = new TimeseriesInContainerSearchResult(annotatableTimeseriesFound);
    return ret;
  }
}
