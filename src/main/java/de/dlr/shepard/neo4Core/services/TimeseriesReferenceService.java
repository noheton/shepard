package de.dlr.shepard.neo4Core.services;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.influxDB.AggregateFunction;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.influxDB.TimeseriesService;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.util.DateHelper;

public class TimeseriesReferenceService {
	private TimeseriesReferenceDAO timeseriesReferenceDAO = new TimeseriesReferenceDAO();
	private TimeseriesService timeseriesService = new TimeseriesService();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private TimeseriesContainerDAO timeseriesContainerDAO = new TimeseriesContainerDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	public List<TimeseriesReference> getAllTimeseriesReferences(long dataObjectId) {
		var references = timeseriesReferenceDAO.findByDataObject(dataObjectId);
		return references;
	}

	public TimeseriesReference getTimeseriesReference(long timeseriesId) {
		var reference = timeseriesReferenceDAO.find(timeseriesId);
		if (reference == null || reference.isDeleted()) {
			return null;
		}
		return reference;
	}

	public TimeseriesReference createTimeseriesReference(long dataObjectId, TimeseriesReferenceIO timeseriesReference,
			String username) throws InvalidBodyException {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.find(dataObjectId);
		var container = timeseriesContainerDAO.find(timeseriesReference.getTimeseriesContainerId());

		if (container == null || container.isDeleted()) {
			throw new InvalidBodyException(String.format("The timeseries container with id %d could not be found.",
					timeseriesReference.getTimeseriesContainerId()));
		}

		var toCreate = new TimeseriesReference();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDataObject(dataObject);
		toCreate.setName(timeseriesReference.getName());
		toCreate.setStart(timeseriesReference.getStart());
		toCreate.setEnd(timeseriesReference.getEnd());
		toCreate.setTimeseries(Arrays.asList(timeseriesReference.getTimeseries()));
		toCreate.setTimeseriesContainer(container);

		var created = timeseriesReferenceDAO.createOrUpdate(toCreate);
		return created;
	}

	public boolean deleteTimeseriesReference(long timeseriesId, String username) {
		var user = userDAO.find(username);

		var old = timeseriesReferenceDAO.find(timeseriesId);
		old.setDeleted(true);
		old.setUpdatedAt(dateHelper.getDate());
		old.setUpdatedBy(user);

		timeseriesReferenceDAO.createOrUpdate(old);
		return true;
	}

	public List<TimeseriesPayload> getPayload(long timeseriesId, AggregateFunction function, Long groupBy,
			Set<String> devicesFilterSet, Set<String> locationsFilterSet, Set<String> symbolicNameFilterSet) {
		var ref = timeseriesReferenceDAO.find(timeseriesId);

		var payload = timeseriesService.getTimeseriesList(ref.getStart(), ref.getEnd(),
				ref.getTimeseriesContainer().getDatabase(), ref.getTimeseries(), function, groupBy, devicesFilterSet,
				locationsFilterSet, symbolicNameFilterSet);

		return payload;
	}

}
