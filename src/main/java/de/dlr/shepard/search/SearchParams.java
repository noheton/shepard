package de.dlr.shepard.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SearchParams {

	@Valid
	@NotBlank
	private String query;
	@Valid
	@NotNull
	private QueryType queryType;

}
