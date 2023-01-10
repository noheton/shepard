package de.dlr.shepard.neo4Core.io;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import de.dlr.shepard.neo4Core.entities.AbstractEntity;
import de.dlr.shepard.util.HasId;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class AbstractEntityIO implements HasId {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private Long id;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@Schema(accessMode = AccessMode.READ_ONLY)
	private Date createdAt;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String createdBy;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@Schema(accessMode = AccessMode.READ_ONLY, nullable = true)
	private Date updatedAt;

	@Schema(accessMode = AccessMode.READ_ONLY, nullable = true)
	private String updatedBy;

	protected AbstractEntityIO(AbstractEntity entity) {
		this.id = entity.getId();
		this.createdAt = entity.getCreatedAt();
		this.createdBy = entity.getCreatedBy() != null ? entity.getCreatedBy().getUsername() : null;
		this.updatedAt = entity.getUpdatedAt();
		this.updatedBy = entity.getUpdatedBy() != null ? entity.getUpdatedBy().getUsername() : null;
	}

	protected static long[] extractIds(List<? extends AbstractEntity> entities) {
		var result = entities.stream().map(e -> e.getId()).mapToLong(Long::longValue).toArray();
		return result;
	}

	@Override
	public String getUniqueId() {
		return id.toString();
	}
}
