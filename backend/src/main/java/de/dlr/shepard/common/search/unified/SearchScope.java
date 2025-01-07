package de.dlr.shepard.common.search.unified;

import de.dlr.shepard.common.util.TraversalRules;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchScope {

  private Long collectionId;
  private Long dataObjectId;

  @NotNull
  private TraversalRules[] traversalRules;
}
