package de.dlr.shepard.neo4Core.io;

import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import de.dlr.shepard.neo4Core.entities.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "Version")
public class VersionIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private UUID uid;

	private String name;

	private String description;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@Schema(accessMode = AccessMode.READ_ONLY)
	private Date createdAt;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String createdBy;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private UUID predecessorUUID;

	public VersionIO(Version version) {
		this.uid = version.getUid();
		this.name = version.getName();
		this.description = version.getDescription();
		this.createdAt = version.getCreatedAt();
		this.createdBy = version.getCreatedBy().getUsername();
		if (version.getPredecessor() != null)
			this.predecessorUUID = version.getPredecessor().getUid();
		else
			this.predecessorUUID = null;
	}
}
