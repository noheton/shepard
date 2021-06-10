package de.dlr.shepard.neo4Core.services;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.PathSegment;

import de.dlr.shepard.exceptions.InvalidPathException;
import de.dlr.shepard.neo4Core.entities.AbstractContainer;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.Constants;

// TODO: FileReference/-Container
public class UrlPathChecker {
	private CollectionService collectionService = new CollectionService();
	private DataObjectService dataObjectService = new DataObjectService();
	private BasicReferenceService basicReferenceService = new BasicReferenceService();
	private DataObjectReferenceService dataObjectReferenceService = new DataObjectReferenceService();
	private URIReferenceService uriReferenceService = new URIReferenceService();

	private TimeseriesReferenceService timeseriesReferenceService = new TimeseriesReferenceService();
	private TimeseriesContainerService timeseriesContainerService = new TimeseriesContainerService();

	private StructuredDataReferenceService structuredDataReferenceService = new StructuredDataReferenceService();
	private StructuredDataContainerService structuredDataContainerService = new StructuredDataContainerService();

	private UserService userService = new UserService();
	private ApiKeyService apiKeyService = new ApiKeyService();
	private SubscriptionService subscrptionService = new SubscriptionService();

	/**
	 * Checks the url for wrong ids. A wrong id could identify a non existing
	 * entity, a previous deleted one, or a non existing association between two
	 * entities. Throws an InvalidPathException in case of an error.
	 * 
	 * @param pathSegments to process
	 * @throws InvalidPathException in case of an invalid path
	 */
	public void checkPathSegments(List<PathSegment> pathSegments) throws InvalidPathException {
		String errorString;
		try {
			errorString = check(pathSegments);
		} catch (NumberFormatException e) {
			throw new InvalidPathException("The given path seems wrong");
		}

		if (!errorString.equals("ok")) {
			throw new InvalidPathException(errorString);
		}

	}

	private String check(List<PathSegment> pathSegments) throws NumberFormatException {
		HashMap<String, String> pathElems = getPathElements(pathSegments);
		StringBuilder builder = new StringBuilder();
		DataObject dataObject = null;
		Collection collection = null;
		User user = null;

		builder.append("ID ERROR - ");

		if (pathElems.containsKey(Constants.COLLECTIONS)) {
			long id = Long.parseLong(pathElems.get(Constants.COLLECTIONS));
			collection = collectionService.getCollection(id);
			String error = checkCollection(collection);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.DATAOBJECTS)) {
			long id = Long.parseLong(pathElems.get(Constants.DATAOBJECTS));
			dataObject = dataObjectService.getDataObject(id);
			String error = checkDataObject(dataObject, collection);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.TIMESERIES_REFERENCES)) {
			long id = Long.parseLong(pathElems.get(Constants.TIMESERIES_REFERENCES));
			var timeseriesReference = timeseriesReferenceService.getTimeseriesReference(id);
			String error = checkReference(timeseriesReference, dataObject);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.TIMESERIES)) {
			long id = Long.parseLong(pathElems.get(Constants.TIMESERIES));
			var timeseriesContainer = timeseriesContainerService.getTimeseriesContainer(id);
			String error = checkContainer(timeseriesContainer);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.STRUCTUREDDATA_REFERENCES)) {
			long id = Long.parseLong(pathElems.get(Constants.STRUCTUREDDATA_REFERENCES));
			var structuredDataReference = structuredDataReferenceService.getStructuredDataReference(id);
			String error = checkReference(structuredDataReference, dataObject);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.STRUCTUREDDATAS)) {
			long id = Long.parseLong(pathElems.get(Constants.STRUCTUREDDATAS));
			var structuredDataContainer = structuredDataContainerService.getStructuredDataContainer(id);
			String error = checkContainer(structuredDataContainer);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.DATAOBJECT_REFERENCES)) {
			long id = Long.parseLong(pathElems.get(Constants.DATAOBJECT_REFERENCES));
			var dataObjectReference = dataObjectReferenceService.getDataObjectReference(id);
			String error = checkReference(dataObjectReference, dataObject);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.URI_REFERENCES)) {
			long id = Long.parseLong(pathElems.get(Constants.URI_REFERENCES));
			var uriReferences = uriReferenceService.getURIReference(id);
			String error = checkReference(uriReferences, dataObject);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.BASIC_REFERENCES)) {
			long id = Long.parseLong(pathElems.get(Constants.BASIC_REFERENCES));
			var reference = basicReferenceService.getBasicReference(id);
			String error = checkReference(reference, dataObject);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.USERS)) {
			user = userService.getUser(pathElems.get(Constants.USERS));
			String error = checkUser(user);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.APIKEYS)) {
			UUID uid = UUID.fromString(pathElems.get(Constants.APIKEYS));
			var apiKey = apiKeyService.getApiKey(uid);
			String error = checkApiKey(apiKey, user);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		if (pathElems.containsKey(Constants.SUBSCRIPTIONS)) {
			long id = Long.parseLong(pathElems.get(Constants.SUBSCRIPTIONS));
			var subscription = subscrptionService.getSubscription(id);
			String error = checkSubscription(subscription, user);
			if (error != null) {
				return builder.append(error).toString();
			}
		}

		return "ok";
	}

	private String checkCollection(Collection collection) {
		if (collection == null) {
			return "Collection does not exist";
		}
		return null;
	}

	private String checkDataObject(DataObject dataObject, Collection collection) {
		if (dataObject == null) {
			return "DataObject does not exist";
		} else if (!dataObject.getCollection().getId().equals(collection.getId())) {
			return "There is no association between experiment and dataObject";
		}
		return null;
	}

	private String checkReference(BasicReference reference, DataObject dataObject) {
		if (reference == null) {
			return "Reference does not exist";
		} else if (!reference.getDataObject().getId().equals(dataObject.getId())) {
			return "There is no association between dataObject and reference";
		}
		return null;
	}

	private String checkContainer(AbstractContainer container) {
		if (container == null) {
			return "Container does not exist";
		}
		return null;
	}

	private String checkUser(User user) {
		if (user == null) {
			return "User does not exist";
		}
		return null;
	}

	private String checkApiKey(ApiKey apiKey, User user) {
		if (apiKey == null) {
			return "ApiKey does not exist";
		} else if (!user.getApiKeys().stream().anyMatch(aKey -> aKey.getUid().equals(apiKey.getUid()))) {
			return "There is no association between apiKey and user";
		}
		return null;
	}

	private String checkSubscription(Subscription subscription, User user) {
		if (subscription == null) {
			return "Subscription does not exist";
		} else if (!user.getSubscriptions().stream().anyMatch(s -> s.getId().equals(subscription.getId()))) {
			return "There is no association between subscription and user";
		}
		return null;
	}

	private HashMap<String, String> getPathElements(List<PathSegment> pathSegments) {
		HashMap<String, String> pathElems = new HashMap<String, String>();
		for (int i = 0; i + 1 < pathSegments.size(); i = i + 2) {
			String value = pathSegments.get(i).toString();
			String id = pathSegments.get(i + 1).toString();
			if (id.isBlank() || id.equals("/"))
				return pathElems;
			pathElems.put(value, id);
		}

		return pathElems;
	}
}
