package de.dlr.shepard.mongoDB;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.dlr.shepard.neo4Core.entities.HasId;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class File implements HasId {

	private String oid;
	@Schema(accessMode = AccessMode.READ_ONLY)
	private String filename;

	@Override
	@JsonIgnore
	public String getUniqueId() {
		return oid;
	}

}
