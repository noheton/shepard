package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.mongoDB.File;
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
public class FileReference extends BasicReference {

	private List<String> filesJson = new ArrayList<String>();

	@ToString.Exclude
	@Relationship(type = Constants.IS_IN_CONTAINER)
	private FileContainer fileContainer;

	/**
	 * For testing purposes only
	 * 
	 * @param id identifies the entity
	 */
	public FileReference(long id) {
		super(id);
	}

	public List<File> getFiles() {
		var mapper = new ObjectMapper();
		TypeReference<File> t = new TypeReference<File>() {
		};
		List<File> result = new ArrayList<File>();
		for (var file : filesJson) {
			try {
				result.add(mapper.readValue(file, t));
			} catch (JsonProcessingException e) {
				log.error("Could not convert file", file);
			}
		}
		return result;
	}

	public void setFiles(List<File> files) {
		var mapper = new ObjectMapper();
		List<String> result = new ArrayList<>();
		for (var file : files) {
			try {
				result.add(mapper.writeValueAsString(file));
			} catch (JsonProcessingException e) {
				log.error("Could not convert file", file);
			}
		}
		filesJson = result;
	}

	public void addFile(File file) {
		var mapper = new ObjectMapper();
		try {
			filesJson.add(mapper.writeValueAsString(file));
		} catch (JsonProcessingException e) {
			log.error("Could not convert file", file);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(filesJson);
		result = prime * result + HasId.hashcodeHelper(fileContainer);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof FileReference))
			return false;
		FileReference other = (FileReference) obj;
		return HasId.equalsHelper(fileContainer, other.fileContainer) && Objects.equals(filesJson, other.filesJson);
	}

}
