package de.dlr.shepard.common.search.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class ASearchResults<T extends ASearchParams> {

  private T searchParams;
}
