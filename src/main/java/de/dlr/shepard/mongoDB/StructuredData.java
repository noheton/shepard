package de.dlr.shepard.mongoDB;

import de.dlr.shepard.neo4Core.entities.HasId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StructuredData implements HasId {

	private String oid;

	@Override
	public String getUniqueId() {
		return oid;
	}

}
