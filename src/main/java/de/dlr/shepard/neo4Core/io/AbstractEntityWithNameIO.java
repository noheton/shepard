package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.AbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Schema(name = "AbstractEntity")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractEntityWithNameIO extends AbstractEntityIO {

	@NotBlank
	@Schema(nullable = true)
	private String name;

	protected AbstractEntityWithNameIO(AbstractEntity entity) {
		super(entity);
		this.name = entity.getName();
	}

}
