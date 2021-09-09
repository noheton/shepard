package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.influxDB.TimeseriesService;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class TimeseriesContainerService {

	private TimeseriesContainerDAO timeseriesContainerDAO = new TimeseriesContainerDAO();
	private TimeseriesService timeseriesService = new TimeseriesService();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	/**
	 * Creates a TimeseriesContainer and stores it in Neo4J
	 *
	 * @param timeseriesContainer to be stored
	 * @param username            of the related user
	 * @return the created timeseriesContainer
	 */
	public TimeseriesContainer createTimeseriesContainer(TimeseriesContainerIO timeseriesContainer, String username) {
		var user = userDAO.find(username);

		var toCreate = new TimeseriesContainer();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDatabase(timeseriesService.createDatabase());
		toCreate.setName(timeseriesContainer.getName());

		return timeseriesContainerDAO.createOrUpdate(toCreate);
	}

	/**
	 * Searches the TimeseriesContainer in Neo4j
	 *
	 * @param id identifies the searched TimeseriesContainer
	 * @return the TimeseriesContainer with matching id or null
	 */
	public TimeseriesContainer getTimeseriesContainer(long id) {
		TimeseriesContainer timeseriesContainer = timeseriesContainerDAO.find(id);
		if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
			return null;
		}
		return timeseriesContainer;
	}

	/**
	 * Searches the database for all TimeseriesContainers
	 *
	 * @param params QueryParamsHelper
	 * @return a list of TimeseriesContainers
	 */
	public List<TimeseriesContainer> getAllTimeseriesContainers(QueryParamHelper params) {
		var containers = timeseriesContainerDAO.findAllTimeseriesContainers(params);
		return containers;
	}

	/**
	 * Deletes a TimeseriesContainer in Neo4j
	 *
	 * @param timeSeriesId identifies the TimeseriesContainer
	 * @param username     of the related user
	 * @return a boolean to determine if TimeseriesContainer was successfully
	 *         deleted
	 */
	public boolean deleteTimeseriesContainer(long timeSeriesId, String username) {
		var user = userDAO.find(username);
		TimeseriesContainer timeseriesContainer = timeseriesContainerDAO.find(timeSeriesId);
		if (timeseriesContainer == null) {
			return false;
		}

		timeseriesContainer.setDeleted(true);
		timeseriesContainer.setUpdatedAt(dateHelper.getDate());
		timeseriesContainer.setUpdatedBy(user);
		timeseriesContainerDAO.createOrUpdate(timeseriesContainer);
		// TODO: Delete database

		return true;
	}

	public Timeseries createTimeseries(long timeseriesId, TimeseriesPayload payload) {
		var timeseriesContainer = timeseriesContainerDAO.find(timeseriesId);
		if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
			return null;
		}
		var result = timeseriesService.createTimeseries(timeseriesContainer.getDatabase(), payload);
		if (result != "") {
			return null;
		}
		return payload.getTimeseries();
	}

}
