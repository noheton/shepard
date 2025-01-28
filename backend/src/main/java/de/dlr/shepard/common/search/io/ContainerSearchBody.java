package de.dlr.shepard.common.search.io;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContainerSearchBody extends ASearchBody<ContainerSearchParams> {

  public ContainerSearchBody(ContainerSearchParams searchParams) {
    super(searchParams);
  }
}
