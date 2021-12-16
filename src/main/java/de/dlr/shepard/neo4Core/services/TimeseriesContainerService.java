package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.influxDB.AggregateFunction;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.influxDB.TimeseriesService;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeseriesContainerService {

	private TimeseriesContainerDAO timeseriesContainerDAO = new TimeseriesContainerDAO();
	private TimeseriesService timeseriesService = new TimeseriesService();
	private PermissionsDAO permissionsDAO = new PermissionsDAO();
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

		var created = timeseriesContainerDAO.createOrUpdate(toCreate);
		permissionsDAO.createOrUpdate(new Permissions(created, user, PermissionType.Private));
		return created;
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
			log.error("Timeseries Container with id {} is null or deleted", id);
			return null;
		}
		return timeseriesContainer;
	}

	/**
	 * Searches the database for all TimeseriesContainers
	 *
	 * @param params   QueryParamsHelper
	 * @param username the name of the user
	 * @return a list of TimeseriesContainers
	 */
	public List<TimeseriesContainer> getAllTimeseriesContainers(QueryParamHelper params, String username) {
		var containers = timeseriesContainerDAO.findAllTimeseriesContainers(params, username);
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
		timeseriesService.deleteDatabase(timeseriesContainer.getDatabase());
		return true;
	}

	/**
	 * Saves timeseries payload in a timeseries container.
	 *
	 * @param timeseriesContainerId identifies the TimeseriesContainer
	 * @param payload               TimeseriesPayload to be created
	 * @return created timeseries
	 */
	public Timeseries createTimeseries(long timeseriesContainerId, TimeseriesPayload payload) {
		var timeseriesContainer = timeseriesContainerDAO.find(timeseriesContainerId);
		if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
			log.error("Timeseries Container with id {} is null or deleted", timeseriesContainerId);
			return null;
		}
		var result = timeseriesService.createTimeseries(timeseriesContainer.getDatabase(), payload);
		if (result != "") {
			log.error("Failed to create timeseries with error: {}", result);
			return null;
		}
		return payload.getTimeseries();
	}

	/**
	 * Loads timeseries payload from a timeseries container.
	 *
	 * @param timeseriesContainerId identifies the TimeseriesContainer
	 * @param timeseries            The timeseries to load
	 * @param start                 The beginning of the timeseries
	 * @param end                   The end of the timeseries
	 * @param function              The aggregate function
	 * @param groupByInterval       The time interval measurements get grouped by
	 * @return TimeseriesPayload
	 */
	public TimeseriesPayload getTimeseries(long timeseriesContainerId, Timeseries timeseries, long start, long end,
			AggregateFunction function, Long groupByInterval) {
		var timeseriesContainer = timeseriesContainerDAO.find(timeseriesContainerId);
		if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
			log.error("Timeseries Container with id {} is null or deleted", timeseriesContainerId);
			return null;
		}
		var result = timeseriesService.getTimeseries(start, end, timeseriesContainer.getDatabase(), timeseries,
				function, groupByInterval);
		return result;
	}

}
