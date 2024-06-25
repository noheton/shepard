package de.dlr.shepard.search.container;

import de.dlr.shepard.search.ASearchBody;
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
