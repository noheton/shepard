package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.converter.StructuredDataConverter;
import de.dlr.shepard.util.Constants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NodeEntity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class StructuredDataReference extends BasicReference {

	@Property("structuredDatasJson")
	@Convert(StructuredDataConverter.class)
	private List<StructuredData> structuredDatas = new ArrayList<>();

	@ToString.Exclude
	@Relationship(type = Constants.IS_IN_CONTAINER)
	private StructuredDataContainer structuredDataContainer;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public StructuredDataReference(long id) {
		super(id);
	}

	public void addStructuredData(StructuredData structuredData) {
		structuredDatas.add(structuredData);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(structuredDatas);
		result = prime * result + HasId.hashcodeHelper(structuredDataContainer);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof StructuredDataReference))
			return false;
		StructuredDataReference other = (StructuredDataReference) obj;
		return HasId.equalsHelper(structuredDataContainer, other.structuredDataContainer)
				&& Objects.equals(structuredDatas, other.structuredDatas);
	}

}
