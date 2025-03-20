package de.dlr.shepard.common.search.io;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserGroupSearchParams extends ASearchParams {

  public UserGroupSearchParams(String query) {
    super(query);
  }
}
