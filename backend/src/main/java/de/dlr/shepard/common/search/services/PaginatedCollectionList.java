package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.context.collection.entities.Collection;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class PaginatedCollectionList {

  private List<Collection> results;
  private Integer totalResults;
  private String query;
  private Optional<Integer> page;
  private Optional<Integer> pageSize;
  private BasicCollectionAttributes orderBy;
  private Boolean orderDesc;

  public PaginatedCollectionList(
    List<Collection> results,
    Integer totalResults,
    String query,
    Optional<Integer> page,
    Optional<Integer> pageSize,
    BasicCollectionAttributes orderBy,
    Boolean orderDesc
  ) {
    this.results = results;
    this.totalResults = totalResults;
    this.query = query;
    this.page = page;
    this.pageSize = pageSize;
    this.orderBy = orderBy;
    this.orderDesc = orderDesc;
  }
}
