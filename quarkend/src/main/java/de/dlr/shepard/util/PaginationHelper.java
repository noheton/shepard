package de.dlr.shepard.util;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class PaginationHelper {

  private int page;
  private int size;

  public int getOffset() {
    return page * size;
  }
}
