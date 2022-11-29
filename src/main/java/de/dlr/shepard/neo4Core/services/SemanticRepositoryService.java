package de.dlr.shepard.neo4Core.services;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.SemanticRepositoryDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.io.SemanticRepositoryIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PaginationHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SemanticRepositoryService {
	private SemanticRepositoryDAO semanticRepositoryDAO = new SemanticRepositoryDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	public List<SemanticRepository> getAllRepositories(PaginationHelper page) {
		var repositories = semanticRepositoryDAO.findAllSemanticRepositories(page);
		return repositories;
	}

	public SemanticRepository getRepository(long id) {
		var repository = semanticRepositoryDAO.find(id);
		if (repository == null || repository.isDeleted()) {
			log.error("Semantic Repository with id {} is null or deleted", id);
			return null;
		}
		return repository;
	}

	public SemanticRepository createRepository(SemanticRepositoryIO repositoryIO, String username) {
		var user = userDAO.find(username);
		var toCreate = new SemanticRepository();

		try {
			new URL(repositoryIO.getEndpoint());
		} catch (MalformedURLException e) {
			log.error("Malformed URL");
			throw new InvalidBodyException("Invalid endpoint");
		}

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
		var repositoy = semanticRepositoryDAO.find(repositoryId);
		if (repositoy == null) {
			return false;
		}
		repositoy.setDeleted(true);
		repositoy.setUpdatedAt(dateHelper.getDate());
		repositoy.setUpdatedBy(user);
		semanticRepositoryDAO.createOrUpdate(repositoy);
		return true;
	}

}
