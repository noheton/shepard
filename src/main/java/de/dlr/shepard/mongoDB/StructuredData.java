package de.dlr.shepard.mongoDB;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
	@JsonIgnore
	public String getUniqueId() {
		return oid;
	}

}
