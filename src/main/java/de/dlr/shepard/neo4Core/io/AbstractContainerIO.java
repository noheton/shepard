package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.AbstractContainer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "AbstractContainer")
public abstract class AbstractContainerIO extends AbstractEntityIO {

	protected AbstractContainerIO(AbstractContainer container) {
		super(container);
	}
}
