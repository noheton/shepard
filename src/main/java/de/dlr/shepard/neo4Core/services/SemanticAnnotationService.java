package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.AbstractEntityDAO;
import de.dlr.shepard.neo4Core.dao.SemanticAnnotationDAO;
import de.dlr.shepard.neo4Core.dao.SemanticRepositoryDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.semantics.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SemanticAnnotationService {

	private SemanticAnnotationDAO semanticAnnotationDAO = new SemanticAnnotationDAO();
	private SemanticRepositoryDAO semanticRepositoryDAO = new SemanticRepositoryDAO();
	private AbstractEntityDAO abstractEntityDAO = new AbstractEntityDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();
	private SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory = new SemanticRepositoryConnectorFactory();

	private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";

	public List<SemanticAnnotation> getAllAnnotations(long entityId) {
		return semanticAnnotationDAO.findAllSemanticAnnotations(entityId);
	}

	public SemanticAnnotation getAnnotation(long id) {
		var annotation = semanticAnnotationDAO.find(id);
		if (annotation == null || annotation.isDeleted()) {
			log.error("File Container with id {} is null or deleted", id);
			return null;
		}
		return annotation;
	}

	public SemanticAnnotation createAnnotation(long entityId, SemanticAnnotationIO annotationIO, String username) {
		var user = userDAO.find(username);
		var entity = abstractEntityDAO.find(entityId);
		if (entity == null || entity.isDeleted())
			throw new InvalidBodyException("invalid entity");

		var propertyRepository = getRepository(annotationIO.getPropertyRepositoryId());
		var valueRepository = getRepository(annotationIO.getValueRepositoryId());
		var name = String.join("-", validateTerm(propertyRepository, annotationIO.getPropertyIRI()),
				validateTerm(valueRepository, annotationIO.getValueIRI()));

		var toCreate = new SemanticAnnotation();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setName(name);
		toCreate.setPropertyIRI(annotationIO.getPropertyIRI());
		toCreate.setValueIRI(annotationIO.getValueIRI());
		toCreate.setPropertyRepository(propertyRepository);
		toCreate.setValueRepository(valueRepository);

		var created = semanticAnnotationDAO.createOrUpdate(toCreate);

		entity.addAnnotation(created);
		abstractEntityDAO.update(entity);

		return created;
	}

	public boolean deleteAnnotation(long id, String username) {
		var user = userDAO.find(username);
		var annotation = semanticAnnotationDAO.find(id);
		if (annotation == null) {
			return false;
		}
		annotation.setDeleted(true);
		annotation.setUpdatedBy(user);
		annotation.setUpdatedAt(dateHelper.getDate());
		semanticAnnotationDAO.createOrUpdate(annotation);
		return true;
	}

	private SemanticRepository getRepository(long id) {
		var repository = semanticRepositoryDAO.find(id);
		if (repository == null || repository.isDeleted())
			throw new InvalidBodyException("invalid repository");

		return repository;
	}

	private String validateTerm(SemanticRepository repository, String iri) {
		var src = semanticRepositoryConnectorFactory.getRepositoryService(repository.getType(),
				repository.getEndpoint());
		var term = src.getTerm(iri);
		if (term == null || term.isEmpty())
			throw new InvalidBodyException("term could not be found");
		return term.getOrDefault(RDFS_LABEL, "");
	}

}
