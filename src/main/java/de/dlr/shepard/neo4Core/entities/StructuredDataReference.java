package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.util.Constants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@NodeEntity
@Getter
@Setter
@ToString
@Log4j2
@NoArgsConstructor
public class StructuredDataReference extends BasicReference {

	private List<String> structuredDatasJson = new ArrayList<>();

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

	public List<StructuredData> getStructuredDatas() {
		var mapper = new ObjectMapper();
		TypeReference<StructuredData> t = new TypeReference<StructuredData>() {
		};
		List<StructuredData> result = new ArrayList<>();
		for (var sd : structuredDatasJson) {
			try {
				result.add(mapper.readValue(sd, t));
			} catch (JsonProcessingException e) {
				log.error("Could not convert structured data", sd);
			}
		}
		return result;
	}

	public void setStructuredDatas(List<StructuredData> structuredDatas) {
		var mapper = new ObjectMapper();
		List<String> result = new ArrayList<>();
		for (var sd : structuredDatas) {
			try {
				result.add(mapper.writeValueAsString(sd));
			} catch (JsonProcessingException e) {
				log.error("Could not convert structured data", sd);
			}
		}
		structuredDatasJson = result;
	}

	public void addStructuredData(StructuredData structuredData) {
		var mapper = new ObjectMapper();
		try {
			structuredDatasJson.add(mapper.writeValueAsString(structuredData));
		} catch (JsonProcessingException e) {
			log.error("Could not convert structured data", structuredData);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(structuredDatasJson);
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
				&& Objects.equals(structuredDatasJson, other.structuredDatasJson);
	}

}
