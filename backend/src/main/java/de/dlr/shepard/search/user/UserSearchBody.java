package de.dlr.shepard.search.user;

import de.dlr.shepard.search.ASearchBody;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserSearchBody extends ASearchBody<UserSearchParams> {

  public UserSearchBody(UserSearchParams searchParams) {
    super(searchParams);
  }
}
