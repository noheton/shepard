package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.FileReference;
import de.dlr.shepard.util.CypherQueryHelper;

public class FileReferenceDAO extends GenericDAO<FileReference> {

	public List<FileReference> findByDataObject(long dataObjectId) {
		String query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
				CypherQueryHelper.getObjectPart("r", "FileReference", false), dataObjectId) + getReturnPart("r");

		var queryResult = findByQuery(query, Collections.emptyMap());

		/*
		 * TODO: This is an ugly workaround to handle large file containers
		 */
		var containerMap = getContainer(dataObjectId);
		List<FileReference> result = StreamSupport.stream(queryResult.spliterator(), false)
				.filter(r -> r.getDataObject() != null).filter(r -> r.getDataObject().getId().equals(dataObjectId))
				.map(r -> {
					r.setFileContainer(new FileContainer(containerMap.get(r.getId())));
					return r;
				}).toList();

		return result;
	}

	@Override
	public Class<FileReference> getEntityType() {
		return FileReference.class;
	}

	private String getReturnPart(String entity) {
		var baseString = "MATCH path=(%s)-[*0..1]-(n) WHERE NOT n:FileContainer RETURN %s, nodes(path), relationships(path)";
		var result = String.format(baseString, entity, entity);
		return result;
	}

	private Map<Long, Long> getContainer(long dataObjectId) {
		String query = String.format(
				"MATCH (d:DataObject)-[hr:has_reference]->%s-[ic:is_in_container]->(c:FileContainer) WHERE ID(d)=%d ",
				CypherQueryHelper.getObjectPart("r", "FileReference", false), dataObjectId) + "RETURN ID(r), ID(c)";

		var qr = getQuery(query, Collections.emptyMap());

		var result = new HashMap<Long, Long>();
		qr.forEach(r -> result.put((Long) r.get("ID(r)"), (Long) r.get("ID(c)")));
		return result;
	}

}
