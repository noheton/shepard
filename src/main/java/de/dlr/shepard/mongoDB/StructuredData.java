package de.dlr.shepard.mongoDB;

import java.util.Date;

import org.bson.Document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class StructuredData extends AbstractMongoObject {

	@Schema(nullable = true)
	private String name;

	public StructuredData(String oid) {
		super(oid);
	}

	public StructuredData(String name, Date createdAt) {
		setCreatedAt(createdAt);
		this.name = name;
	}

	public StructuredData(String oid, Date createdAt, String name) {
		super(oid, createdAt);
		this.name = name;
	}

	/**
	 * Converts a document to StructuredData
	 *
	 * @param doc
	 */
	public StructuredData(Document doc) {
		super(doc.getString("oid"), doc.getDate("createdAt"));
		this.name = doc.getString("name");
	}
}
