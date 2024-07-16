package de.dlr.shepard.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class ASearchResults<T extends ASearchParams> {

  private T searchParams;
}
