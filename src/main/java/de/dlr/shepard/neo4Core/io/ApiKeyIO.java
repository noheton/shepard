package de.dlr.shepard.neo4Core.io;

import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import de.dlr.shepard.neo4Core.entities.ApiKey;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "ApiKey")
public class ApiKeyIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private UUID uid;

	private String name;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@Schema(accessMode = AccessMode.READ_ONLY)
	private Date createdAt;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String belongsTo;

	public ApiKeyIO(ApiKey key) {
		this.uid = key.getUid();
		this.name = key.getName();
		this.createdAt = key.getCreatedAt();
		this.belongsTo = key.getBelongsTo() != null ? key.getBelongsTo().getUsername() : null;
	}
}
