package de.dlr.shepard.mongoDB;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class File extends AbstractMongoObject {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String filename;

	public File(String oid, String filename) {
		super(oid);
		this.filename = filename;
	}

	public File(Date createdAt, String filename) {
		setCreatedAt(createdAt);
		this.filename = filename;
	}

	public File(String oid, Date createdAt, String filename) {
		super(oid, createdAt);
		this.filename = filename;
	}
}
