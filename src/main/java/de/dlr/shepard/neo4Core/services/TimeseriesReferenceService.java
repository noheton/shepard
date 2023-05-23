package de.dlr.shepard.neo4Core.services;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.influxDB.InfluxUtil;
import de.dlr.shepard.influxDB.SingleValuedUnaryFunction;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.influxDB.TimeseriesService;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeseriesReferenceService implements IReferenceService<TimeseriesReference, TimeseriesReferenceIO> {
	private TimeseriesReferenceDAO timeseriesReferenceDAO = new TimeseriesReferenceDAO();
	private TimeseriesService timeseriesService = new TimeseriesService();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private TimeseriesContainerDAO timeseriesContainerDAO = new TimeseriesContainerDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();
	private PermissionsUtil permissionsUtil = new PermissionsUtil();

	@Override
	public List<TimeseriesReference> getAllReferences(long dataObjectId) {
		var references = timeseriesReferenceDAO.findByDataObject(dataObjectId);
		return references;
	}

	@Override
	public TimeseriesReference getReference(long id) {
		var reference = timeseriesReferenceDAO.find(id);
		if (reference == null || reference.isDeleted()) {
			log.error("Timeseries Reference with id {} is null or deleted", id);
			return null;
		}
		return reference;
	}

	@Override
	public TimeseriesReference createReference(long dataObjectId, TimeseriesReferenceIO timeseriesReference,
			String username) {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.find(dataObjectId);
		var container = timeseriesContainerDAO.find(timeseriesReference.getTimeseriesContainerId());

		if (container == null || container.isDeleted()) {
			throw new InvalidBodyException(String.format("The timeseries container with id %d could not be found.",
					timeseriesReference.getTimeseriesContainerId()));
		}

		// sanitize timeseries
		var errors = Arrays.stream(timeseriesReference.getTimeseries()).map(InfluxUtil::sanitize)
				.filter(e -> !e.isBlank()).toList();
		if (!errors.isEmpty())
			throw new InvalidBodyException(
					"The timeseries list contains illegal characters: " + String.join(", ", errors));

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

	@Override
	public boolean deleteReference(long timeseriesId, String username) {
		var user = userDAO.find(username);

		var old = timeseriesReferenceDAO.find(timeseriesId);
		old.setDeleted(true);
		old.setUpdatedAt(dateHelper.getDate());
		old.setUpdatedBy(user);

		timeseriesReferenceDAO.createOrUpdate(old);
		return true;
	}

	public List<TimeseriesPayload> getTimeseriesPayload(long timeseriesId, SingleValuedUnaryFunction function,
			Long groupBy, FillOption fillOption, Set<String> devicesFilterSet, Set<String> locationsFilterSet,
			Set<String> symbolicNameFilterSet, String username) {
		var ref = timeseriesReferenceDAO.find(timeseriesId);
		var containerId = ref.getTimeseriesContainer().getId();
		var database = ref.getTimeseriesContainer().getDatabase();

		if (!permissionsUtil.isAllowed(containerId, AccessType.Read, username))
			throw new InvalidAuthException("You are not authorized to access this timeseries");

		return timeseriesService.getTimeseriesPayloadList(ref.getStart(), ref.getEnd(), database, ref.getTimeseries(),
				function, groupBy, fillOption, devicesFilterSet, locationsFilterSet, symbolicNameFilterSet);
	}

	public InputStream exportTimeseriesPayload(long timeseriesId, SingleValuedUnaryFunction function, Long groupBy,
			FillOption fillOption, Set<String> devicesFilterSet, Set<String> locationsFilterSet,
			Set<String> symbolicNameFilterSet, String username) throws IOException {
		var ref = timeseriesReferenceDAO.find(timeseriesId);
		var containerId = ref.getTimeseriesContainer().getId();
		var database = ref.getTimeseriesContainer().getDatabase();

		if (!permissionsUtil.isAllowed(containerId, AccessType.Read, username))
			throw new InvalidAuthException("You are not authorized to access this timeseries");

		return timeseriesService.exportTimeseriesPayload(ref.getStart(), ref.getEnd(), database, ref.getTimeseries(),
				function, groupBy, fillOption, devicesFilterSet, locationsFilterSet, symbolicNameFilterSet);
	}

}
