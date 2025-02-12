package de.dlr.shepard.common.search.io;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CollectionSearchParams extends ASearchParams {

  public CollectionSearchParams(String query) {
    super(query);
  }
}
