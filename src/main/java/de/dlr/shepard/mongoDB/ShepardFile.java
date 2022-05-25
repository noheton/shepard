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
public class ShepardFile extends AbstractMongoObject {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String filename;
	@Schema(accessMode = AccessMode.READ_ONLY, nullable = true)
	private String md5;

	public ShepardFile(Date createdAt, String filename, String md5) {
		setCreatedAt(createdAt);
		this.filename = filename;
		this.md5 = md5;
	}

	public ShepardFile(String oid, Date createdAt, String filename, String md5) {
		super(oid, createdAt);
		this.filename = filename;
		this.md5 = md5;
	}

}
