package de.dlr.shepard.data.semantic.services;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.semantic.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.data.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.data.semantic.entities.SemanticRepository;
import de.dlr.shepard.data.semantic.io.SemanticRepositoryIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@RequestScoped
public class SemanticRepositoryService {

  private SemanticRepositoryDAO semanticRepositoryDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;
  private SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

  SemanticRepositoryService() {}

  @Inject
  public SemanticRepositoryService(
    SemanticRepositoryDAO semanticRepositoryDAO,
    UserDAO userDAO,
    DateHelper dateHelper,
    SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory
  ) {
    this.semanticRepositoryDAO = semanticRepositoryDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.semanticRepositoryConnectorFactory = semanticRepositoryConnectorFactory;
  }

  public List<SemanticRepository> getAllRepositories(QueryParamHelper params) {
    var repositories = semanticRepositoryDAO.findAllSemanticRepositories(params);
    return repositories;
  }

  public SemanticRepository getRepository(long id) {
    var repository = semanticRepositoryDAO.findByNeo4jId(id);
    if (repository == null || repository.isDeleted()) {
      Log.errorf("Semantic Repository with id %s is null or deleted", id);
      return null;
    }
    return repository;
  }

  public SemanticRepository createRepository(SemanticRepositoryIO repositoryIO, String username) {
    var user = userDAO.find(username);
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

  public boolean deleteRepository(long repositoryId, String username) {
    var user = userDAO.find(username);
    var repositoy = semanticRepositoryDAO.findByNeo4jId(repositoryId);
    if (repositoy == null) {
      return false;
    }
    repositoy.setDeleted(true);
    repositoy.setUpdatedAt(dateHelper.getDate());
    repositoy.setUpdatedBy(user);
    semanticRepositoryDAO.createOrUpdate(repositoy);
    return true;
  }

  private void validateRepository(SemanticRepositoryIO repository) {
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
