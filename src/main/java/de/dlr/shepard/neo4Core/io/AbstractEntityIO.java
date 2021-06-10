package de.dlr.shepard.neo4Core.io;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import de.dlr.shepard.neo4Core.entities.AbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "AbstractEntity")
public abstract class AbstractEntityIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private Long id;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@Schema(accessMode = AccessMode.READ_ONLY)
	private Date createdAt;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String createdBy;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@Schema(accessMode = AccessMode.READ_ONLY)
	private Date updatedAt;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String updatedBy;

	public AbstractEntityIO(AbstractEntity entity) {
		this.id = entity.getId();
		this.createdAt = entity.getCreatedAt();
		this.createdBy = entity.getCreatedBy() != null ? entity.getCreatedBy().getUsername() : null;
		this.updatedAt = entity.getUpdatedAt();
		this.updatedBy = entity.getUpdatedBy() != null ? entity.getUpdatedBy().getUsername() : null;
	}

	protected long[] extractIds(List<? extends AbstractEntity> entities) {
		long[] result = new long[entities.size()];
		for (int i = 0; i < entities.size(); i++) {
			result[i] = entities.get(i).getId();
		}
		return result;
	}
}
