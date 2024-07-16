package de.dlr.shepard.search.user;

import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.search.ASearchResults;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserSearchResult extends ASearchResults<UserSearchParams> {

  private UserIO[] results;

  public UserSearchResult(UserIO[] results, UserSearchParams searchParams) {
    super(searchParams);
    this.results = results;
  }
}
