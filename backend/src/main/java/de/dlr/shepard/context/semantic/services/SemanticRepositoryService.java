package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.semantic.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@RequestScoped
public class SemanticRepositoryService {

  @Inject
  SemanticRepositoryDAO semanticRepositoryDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

  public List<SemanticRepository> getAllRepositories(QueryParamHelper params) {
    var repositories = semanticRepositoryDAO.findAllSemanticRepositories(params);
    return repositories;
  }

  /**
   * Returns a semantic repository by Id
   * @param id
   * @return SemanticRepository
   * @throws InvalidPathException if repository could not be found
   */
  public SemanticRepository getRepository(long id) {
    var repository = semanticRepositoryDAO.findByNeo4jId(id);
    if (repository == null || repository.isDeleted()) {
      String errorMsg = "ID ERROR - Semantic Repository with id %s is null or deleted".formatted(id);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    return repository;
  }

  public SemanticRepository createRepository(SemanticRepositoryIO repositoryIO) {
    User user = userService.getCurrentUser();
    var toCreate = new SemanticRepository();
    validateRepository(repositoryIO);

    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setName(repositoryIO.getName());
    toCreate.setType(repositoryIO.getType());
    toCreate.setEndpoint(repositoryIO.getEndpoint());

    var created = semanticRepositoryDAO.createOrUpdate(toCreate);
    return created;
  }

  /**
   * Deletes a semantic repository by Id
   *
   * @param repositoryId
   * @throws InvalidPathException if repository with repositoryId does not exist
   */
  public void deleteRepository(long repositoryId) {
    SemanticRepository repository = getRepository(repositoryId);

    User user = userService.getCurrentUser();
    repository.setDeleted(true);
    repository.setUpdatedAt(dateHelper.getDate());
    repository.setUpdatedBy(user);
    semanticRepositoryDAO.createOrUpdate(repository);
  }

  private void validateRepository(SemanticRepositoryIO repository) {
    // N1a — INTERNAL connector ignores the endpoint field entirely; skip URL
    // format validation and health-check so operators can create/update INTERNAL
    // repositories without supplying a dummy URL.
    if (repository.getType() == SemanticRepositoryType.INTERNAL) {
      return;
    }
    try {
      new URL(repository.getEndpoint());
    } catch (MalformedURLException e) {
      Log.errorf("Malformed URL: %s", repository.getEndpoint());
      throw new InvalidBodyException("Invalid endpoint");
    }
    var src = semanticRepositoryConnectorFactory.getRepositoryService(repository.getType(), repository.getEndpoint());
    var alive = src.healthCheck();
    if (!alive) {
      Log.errorf("Endpoint not alive: %s", repository.getEndpoint());
      throw new InvalidBodyException("Invalid endpoint");
    }
  }
}
