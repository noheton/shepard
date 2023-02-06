package de.dlr.shepard.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContainerSearchBody {

	@Valid
	@NotNull
	private ContainerSearchParams searchParams;

}
