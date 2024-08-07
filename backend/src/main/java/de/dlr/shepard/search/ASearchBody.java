package de.dlr.shepard.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ASearchBody<T extends ASearchParams> {

  @Valid
  @NotNull
  private T searchParams;
}
