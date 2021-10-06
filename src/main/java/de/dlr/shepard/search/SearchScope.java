package de.dlr.shepard.search;

import de.dlr.shepard.util.TraversalRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SearchScope {

	@Valid
	@NotNull
	private Integer collectionId;
	@Valid
	@NotNull
	private Integer dataObjectId;
	@Valid
	@NotNull
	private TraversalRules[] traversalRules;

}
