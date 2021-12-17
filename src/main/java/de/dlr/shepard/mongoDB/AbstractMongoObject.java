package de.dlr.shepard.mongoDB;

import java.util.Date;

import org.bson.codecs.pojo.annotations.BsonIgnore;

import com.fasterxml.jackson.annotation.JsonFormat;

import de.dlr.shepard.util.HasId;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class AbstractMongoObject implements HasId {

	@BsonIgnore
	@Schema(accessMode = AccessMode.READ_ONLY)
	private String oid;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@Schema(accessMode = AccessMode.READ_ONLY, nullable = true)
	private Date createdAt;

	/**
	 * Constructor
	 *
	 * @param oid Object Identifier
	 */
	protected AbstractMongoObject(String oid) {
		this.oid = oid;
	}

	@BsonIgnore
	@Override
	public String getUniqueId() {
		return oid;
	}

}
