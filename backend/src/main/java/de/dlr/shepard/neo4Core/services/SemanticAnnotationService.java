package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.SemanticAnnotationDAO;
import de.dlr.shepard.neo4Core.dao.SemanticRepositoryDAO;
import de.dlr.shepard.neo4Core.dao.VersionableEntityConcreteDAO;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.entities.VersionableEntity;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.semantics.SemanticRepositoryConnectorFactory;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestScoped
public class SemanticAnnotationService {

  private SemanticAnnotationDAO semanticAnnotationDAO;
  private SemanticRepositoryDAO semanticRepositoryDAO;
  private VersionableEntityConcreteDAO versionableEntityConcreteDAO;
  private SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

  SemanticAnnotationService() {}

  @Inject
  public SemanticAnnotationService(
    SemanticAnnotationDAO semanticAnnotationDAO,
    SemanticRepositoryDAO semanticRepositoryDAO,
    VersionableEntityConcreteDAO versionableEntityConcreteDAO,
    SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory
  ) {
    this.semanticAnnotationDAO = semanticAnnotationDAO;
    this.semanticRepositoryDAO = semanticRepositoryDAO;
    this.versionableEntityConcreteDAO = versionableEntityConcreteDAO;
    this.semanticRepositoryConnectorFactory = semanticRepositoryConnectorFactory;
  }

  public List<SemanticAnnotation> getAllAnnotationsByNeo4jId(long entityId) {
    return semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(entityId);
  }

  public List<SemanticAnnotation> getAllAnnotationsByShepardId(long shepardId) {
    return semanticAnnotationDAO.findAllSemanticAnnotationsByShepardId(shepardId);
  }

  public SemanticAnnotation getAnnotationByNeo4jId(long id) {
    var annotation = semanticAnnotationDAO.findByNeo4jId(id);
    if (annotation == null) {
      log.error("Semantic Annotation with id {} is null or deleted", id);
      return null;
    }
    return annotation;
  }

  public SemanticAnnotation createAnnotationByShepardId(long entityShepardId, SemanticAnnotationIO annotationIO) {
    VersionableEntity entity = versionableEntityConcreteDAO.findByShepardId(entityShepardId);
    if (entity == null || entity.isDeleted()) throw new InvalidBodyException("invalid entity");

    SemanticRepository propertyRepository = getRepository(annotationIO.getPropertyRepositoryId());
    SemanticRepository valueRepository = getRepository(annotationIO.getValueRepositoryId());
    String name = String.join(
      "::",
      validateTerm(propertyRepository, annotationIO.getPropertyIRI()),
      validateTerm(valueRepository, annotationIO.getValueIRI())
    );

    SemanticAnnotation toCreate = new SemanticAnnotation();
    toCreate.setName(name);
    toCreate.setPropertyIRI(annotationIO.getPropertyIRI());
    toCreate.setValueIRI(annotationIO.getValueIRI());
    toCreate.setPropertyRepository(propertyRepository);
    toCreate.setValueRepository(valueRepository);
    SemanticAnnotation created = semanticAnnotationDAO.createOrUpdate(toCreate);
    entity.addAnnotation(created);
    versionableEntityConcreteDAO.createOrUpdate(entity);
    return created;
  }

  public boolean deleteAnnotationByNeo4jId(long id) {
    var result = semanticAnnotationDAO.deleteByNeo4jId(id);
    return result;
  }

  private SemanticRepository getRepository(long id) {
    var repository = semanticRepositoryDAO.findByNeo4jId(id);
    if (repository == null || repository.isDeleted()) throw new InvalidBodyException("invalid repository");

    return repository;
  }

  private String validateTerm(SemanticRepository repository, String iri) {
    var src = semanticRepositoryConnectorFactory.getRepositoryService(repository.getType(), repository.getEndpoint());
    var term = src.getTerm(iri);
    if (term == null || term.isEmpty()) throw new InvalidBodyException("term could not be found");
    // Prefer the default label
    if (term.containsKey("")) return term.get("");
    // Then prefer the English label
    if (term.containsKey("en")) return term.get("en");
    // Fall back to the first label in the list
    return term.values().iterator().next();
  }
}
