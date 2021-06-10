package de.dlr.shepard.neo4Core.io;

import java.util.HashMap;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.AbstractDataObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "AbstractDataObject")
public abstract class AbstractDataObjectIO extends AbstractEntityIO {

	private String name;

	private String description;

	private Map<String, String> attributes = new HashMap<>();

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long[] incomingIds;

	public AbstractDataObjectIO(AbstractDataObject dataObject) {
		super(dataObject);
		this.name = dataObject.getName();
		this.description = dataObject.getDescription();
		this.attributes = dataObject.getAttributes();
		this.incomingIds = extractIds(dataObject.getIncoming());
	}

}
