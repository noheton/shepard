package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.context.semantic.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.version.daos.VersionableEntityConcreteDAO;
import de.dlr.shepard.context.version.entities.VersionableEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.nio.file.InvalidPathException;
import java.util.List;

@RequestScoped
public class SemanticAnnotationService {

  @Inject
  SemanticAnnotationDAO semanticAnnotationDAO;

  @Inject
  SemanticRepositoryService semanticRepositoryService;

  @Inject
  VersionableEntityConcreteDAO versionableEntityConcreteDAO;

  @Inject
  SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

  public List<SemanticAnnotation> getAllAnnotationsByNeo4jId(long entityId) {
    return semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(entityId);
  }

  public List<SemanticAnnotation> getAllAnnotationsByShepardId(long shepardId) {
    return semanticAnnotationDAO.findAllSemanticAnnotationsByShepardId(shepardId);
  }

  public SemanticAnnotation getAnnotationByNeo4jId(long id) {
    var annotation = semanticAnnotationDAO.findByNeo4jId(id);
    if (annotation == null) {
      String errorMsg = "ID ERROR - Semantic Annotation with id %s is null or deleted".formatted(id);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    return annotation;
  }

  public SemanticAnnotation createAnnotationByShepardId(long entityShepardId, SemanticAnnotationIO annotationIO) {
    VersionableEntity entity = versionableEntityConcreteDAO.findByShepardId(entityShepardId);
    if (entity == null || entity.isDeleted()) throw new InvalidBodyException("invalid entity");

    SemanticRepository propertyRepository = getRepository(annotationIO.getPropertyRepositoryId());
    SemanticRepository valueRepository = getRepository(annotationIO.getValueRepositoryId());
    var propertyName = validateTerm(propertyRepository, annotationIO.getPropertyIRI());
    var valueName = validateTerm(valueRepository, annotationIO.getValueIRI());

    SemanticAnnotation toCreate = new SemanticAnnotation();
    toCreate.setPropertyIRI(annotationIO.getPropertyIRI());
    toCreate.setValueIRI(annotationIO.getValueIRI());
    toCreate.setPropertyRepository(propertyRepository);
    toCreate.setValueRepository(valueRepository);
    toCreate.setPropertyName(propertyName);
    toCreate.setValueName(valueName);

    SemanticAnnotation created = semanticAnnotationDAO.createOrUpdate(toCreate);
    entity.addAnnotation(created);
    versionableEntityConcreteDAO.createOrUpdate(entity);
    return created;
  }

  public boolean deleteAnnotationByNeo4jId(long id) {
    getAnnotationByNeo4jId(id);
    var result = semanticAnnotationDAO.deleteByNeo4jId(id);
    return result;
  }

  private SemanticRepository getRepository(long id) {
    try {
      return semanticRepositoryService.getRepository(id);
    } catch (InvalidPathException ex) {
      Log.error(ex.getMessage());
      throw new NotFoundException(ex.getMessage());
    }
  }

  public String validateTerm(SemanticRepository repository, String iri) {
    var src = semanticRepositoryConnectorFactory.getRepositoryService(repository.getType(), repository.getEndpoint());
    var term = src.getTerm(iri);
    if (term == null || term.isEmpty()) throw new InvalidBodyException("term could not be found");
    if (term.containsKey("")) return term.get("");
    if (term.containsKey("en")) return term.get("en");
    return term.values().iterator().next();
  }

  // N1l — non-throwing label resolution for the snapshot-refresh job
  private String resolveLabelOrNull(SemanticRepository repository, String iri) {
    if (repository == null || iri == null || iri.isBlank()) return null;
    try {
      var src = semanticRepositoryConnectorFactory.getRepositoryService(
          repository.getType(), repository.getEndpoint());
      var term = src.getTerm(iri);
      if (term == null || term.isEmpty()) return null;
      if (term.containsKey("")) return term.get("");
      if (term.containsKey("en")) return term.get("en");
      return term.values().iterator().next();
    } catch (RuntimeException ex) {
      Log.warnf(
          "SemanticAnnotationService.resolveLabelOrNull: could not resolve <%s> (%s)",
          iri, ex.getMessage());
      return null;
    }
  }

  // N1l — opt-in admin job to refresh stale propertyName/valueName snapshots
  public int refreshStaleSnapshots() {
    final int BATCH = 500;
    int skip = 0;
    int updated = 0;

    while (true) {
      List<SemanticAnnotation> page = semanticAnnotationDAO.findPaginated(skip, BATCH);
      if (page.isEmpty()) break;

      for (SemanticAnnotation ann : page) {
        boolean dirty = false;

        String newPropName = resolveLabelOrNull(ann.getPropertyRepository(), ann.getPropertyIRI());
        if (newPropName != null && !newPropName.equals(ann.getPropertyName())) {
          ann.setPropertyName(newPropName);
          dirty = true;
        }

        String newValName = resolveLabelOrNull(ann.getValueRepository(), ann.getValueIRI());
        if (newValName != null && !newValName.equals(ann.getValueName())) {
          ann.setValueName(newValName);
          dirty = true;
        }

        if (dirty) {
          semanticAnnotationDAO.createOrUpdate(ann);
          updated++;
        }
      }

      if (page.size() < BATCH) break;
      skip += BATCH;
    }

    Log.infof("SemanticAnnotationService.refreshStaleSnapshots: updated %d annotation(s)", updated);
    return updated;
  }
}
