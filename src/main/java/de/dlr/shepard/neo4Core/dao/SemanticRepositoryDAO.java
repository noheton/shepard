package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.util.CypherQueryHelper;
import de.dlr.shepard.util.PaginationHelper;

public class SemanticRepositoryDAO extends GenericDAO<SemanticRepository> {

	public List<SemanticRepository> findAllSemanticRepositories(PaginationHelper page) {
		Map<String, Object> paramsMap = new HashMap<>();
		var query = String.format("MATCH %s WITH r", CypherQueryHelper.getObjectPart("r", "SemanticRepository", false));
		if (page != null) {
			query += " " + CypherQueryHelper.getPaginationPart();
			paramsMap.put("offset", page.getOffset());
			paramsMap.put("size", page.getSize());
		}
		query += " " + CypherQueryHelper.getReturnPart("r");
		var result = new ArrayList<SemanticRepository>();
		for (var repository : findByQuery(query, paramsMap)) {
			result.add(repository);
		}

		return result;
	}

	@Override
	public Class<SemanticRepository> getEntityType() {
		return SemanticRepository.class;
	}

}
