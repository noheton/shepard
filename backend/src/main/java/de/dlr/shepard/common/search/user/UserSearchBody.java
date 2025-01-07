package de.dlr.shepard.common.search.user;

import de.dlr.shepard.common.search.ASearchBody;
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
