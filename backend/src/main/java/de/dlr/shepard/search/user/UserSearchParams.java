package de.dlr.shepard.search.user;

import de.dlr.shepard.search.ASearchParams;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserSearchParams extends ASearchParams {

  public UserSearchParams(String query) {
    super(query);
  }
}
