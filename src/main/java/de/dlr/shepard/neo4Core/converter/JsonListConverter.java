package de.dlr.shepard.neo4Core.converter;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.ogm.typeconversion.AttributeConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class JsonListConverter<T> implements AttributeConverter<List<T>, List<String>> {

	private ObjectMapper mapper = new ObjectMapper();

	@Override
	public List<String> toGraphProperty(List<T> value) {
		var result = new ArrayList<String>();
		for (var obj : value) {
			try {
				result.add(mapper.writeValueAsString(obj));
			} catch (JsonProcessingException e) {
				log.error("Could not convert object {}", obj);
			}
		}
		return result;
	}

	@Override
	public List<T> toEntityAttribute(List<String> value) {
		var result = new ArrayList<T>(value.size());
		for (var obj : value) {
			try {
				result.add(mapper.readValue(obj, getEntityType()));
			} catch (JsonProcessingException e) {
				log.error("Could not convert object {}", obj);
			}
		}
		return result;
	}

	abstract Class<T> getEntityType();
}
