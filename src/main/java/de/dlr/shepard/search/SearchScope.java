package de.dlr.shepard.search;

import de.dlr.shepard.util.TraversalRules;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SearchScope {

	@NotNull
	private Long collectionId;
	@NotNull
	private Long dataObjectId;
	@NotNull
	private TraversalRules[] traversalRules;

}
