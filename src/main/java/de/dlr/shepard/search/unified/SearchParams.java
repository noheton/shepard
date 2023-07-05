package de.dlr.shepard.search.unified;

import de.dlr.shepard.search.ASearchParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SearchParams extends ASearchParams {

	@Valid
	@NotNull
	private QueryType queryType;

	public SearchParams(String query, QueryType queryType) {
		super(query);
		this.queryType = queryType;
	}

}
