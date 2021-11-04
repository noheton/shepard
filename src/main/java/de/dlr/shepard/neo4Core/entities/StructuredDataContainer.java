package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.converter.StructuredDataConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StructuredDataContainer extends AbstractContainer {

	private String mongoId;

	@Property("structuredDatasJson")
	@Convert(StructuredDataConverter.class)
	private List<StructuredData> structuredDatas = new ArrayList<>();

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public StructuredDataContainer(long id) {
		super(id);
	}

	public void addStructuredData(StructuredData structuredData) {
		structuredDatas.add(structuredData);
	}

}
