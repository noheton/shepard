package de.dlr.shepard.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SearchBody {

	@Valid
	@NotEmpty
	private SearchScope[] scopes;
	@Valid
	@NotNull
	private SearchParams searchParams;

}
