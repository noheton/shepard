package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.BasicContainer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "BasicContainer")
public class BasicContainerIO extends BasicEntityIO {

	public BasicContainerIO(BasicContainer container) {
		super(container);
	}

}
