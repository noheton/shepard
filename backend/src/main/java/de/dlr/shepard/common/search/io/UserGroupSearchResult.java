package de.dlr.shepard.common.search.io;

import de.dlr.shepard.auth.users.io.UserGroupIO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserGroupSearchResult extends ASearchResults<UserGroupSearchParams> {

  private UserGroupIO[] results;

  public UserGroupSearchResult(UserGroupIO[] results, UserGroupSearchParams searchParams) {
    super(searchParams);
    this.results = results;
  }
}
