package de.dlr.shepard.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchParams {

	@Valid
	@NotBlank
	private String query;
	@Valid
	@NotNull
	private QueryType queryType;

}
