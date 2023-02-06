package de.dlr.shepard.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContainerSearchParams {

	@Valid
	@NotBlank
	private String query;
	@Valid
	private ContainerQueryType queryType;

}
