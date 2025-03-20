package de.dlr.shepard.common.search.io;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserGroupSearchBody extends ASearchBody<UserGroupSearchParams> {

  public UserGroupSearchBody(UserGroupSearchParams searchParams) {
    super(searchParams);
  }
}
