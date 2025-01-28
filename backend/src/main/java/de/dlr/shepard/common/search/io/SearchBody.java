package de.dlr.shepard.common.search.io;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SearchBody extends ASearchBody<SearchParams> {

  @Valid
  @NotEmpty
  private SearchScope[] scopes;

  public SearchBody(SearchScope[] scopes, SearchParams searchParams) {
    super(searchParams);
    this.scopes = scopes;
  }
}
