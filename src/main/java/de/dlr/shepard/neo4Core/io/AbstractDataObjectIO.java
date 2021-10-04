package de.dlr.shepard.neo4Core.io;

import java.util.HashMap;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.AbstractDataObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "AbstractDataObject")
public abstract class AbstractDataObjectIO extends AbstractEntityIO {

	@NotBlank
	@Schema(nullable = true)
	private String name;

	@Schema(nullable = true)
	private String description;

	private Map<String, String> attributes = new HashMap<>();

	public AbstractDataObjectIO(AbstractDataObject dataObject) {
		super(dataObject);
		this.name = dataObject.getName();
		this.description = dataObject.getDescription();
		this.attributes = dataObject.getAttributes();
	}

}
