package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.exceptions.InvalidPathException;
import de.dlr.shepard.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.BasicContainer;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.timeseries.services.ExperimentalTimeseriesContainerService;
import de.dlr.shepard.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

@RequestScoped
public class UrlPathChecker {

  private CollectionService collectionService;
  private DataObjectService dataObjectService;
  private BasicReferenceService basicReferenceService;
  private CollectionReferenceService collectionReferenceService;
  private DataObjectReferenceService dataObjectReferenceService;
  private URIReferenceService uriReferenceService;
  private TimeseriesReferenceService timeseriesReferenceService;
  private TimeseriesContainerService timeseriesContainerService;
  private ExperimentalTimeseriesContainerService experimentalTimeseriesContainerService;
  private StructuredDataReferenceService structuredDataReferenceService;
  private StructuredDataContainerService structuredDataContainerService;
  private FileReferenceService fileReferenceService;
  private FileContainerService fileContainerService;
  private UserService userService;
  private ApiKeyService apiKeyService;
  private SubscriptionService subscriptionService;
  private UserGroupService userGroupService;
  private LabJournalEntryService labJournalService;

  private SemanticRepositoryService semanticRepositoryService;

  private SemanticAnnotationService semanticAnnotationService;

  UrlPathChecker() {}

  @Inject
  public UrlPathChecker(
    CollectionService collectionService,
    DataObjectService dataObjectService,
    BasicReferenceService basicReferenceService,
    CollectionReferenceService collectionReferenceService,
    DataObjectReferenceService dataObjectReferenceService,
    URIReferenceService uriReferenceService,
    TimeseriesReferenceService timeseriesReferenceService,
    TimeseriesContainerService timeseriesContainerService,
    ExperimentalTimeseriesContainerService experimentalTimeseriesContainerService,
    StructuredDataReferenceService structuredDataReferenceService,
    StructuredDataContainerService structuredDataContainerService,
    FileReferenceService fileReferenceService,
    FileContainerService fileContainerService,
    UserService userService,
    ApiKeyService apiKeyService,
    SubscriptionService subscriptionService,
    UserGroupService userGroupService,
    SemanticRepositoryService semanticRepositoryService,
    SemanticAnnotationService semanticAnnotationService,
    LabJournalEntryService labJournalService
  ) {
    this.collectionService = collectionService;
    this.dataObjectService = dataObjectService;
    this.basicReferenceService = basicReferenceService;
    this.collectionReferenceService = collectionReferenceService;
    this.dataObjectReferenceService = dataObjectReferenceService;
    this.uriReferenceService = uriReferenceService;
    this.timeseriesReferenceService = timeseriesReferenceService;
    this.timeseriesContainerService = timeseriesContainerService;
    this.experimentalTimeseriesContainerService = experimentalTimeseriesContainerService;
    this.structuredDataReferenceService = structuredDataReferenceService;
    this.structuredDataContainerService = structuredDataContainerService;
    this.fileReferenceService = fileReferenceService;
    this.fileContainerService = fileContainerService;
    this.userService = userService;
    this.apiKeyService = apiKeyService;
    this.subscriptionService = subscriptionService;
    this.userGroupService = userGroupService;
    this.semanticRepositoryService = semanticRepositoryService;
    this.semanticAnnotationService = semanticAnnotationService;
    this.labJournalService = labJournalService;
  }

  /**
   * Checks the url for wrong ids. A wrong id could identify a non existing
   * entity, a previous deleted one, or a non existing association between two
   * entities. Throws an InvalidPathException in case of an error.
   *
   * @param pathSegments to process
   * @param multivaluedMap query params map
   */
  public void check(List<PathSegment> pathSegments, MultivaluedMap<String, String> queryParams) {
    String errorString;
    try {
      errorString = checkForError(pathSegments, queryParams);
    } catch (NumberFormatException e) {
      throw new InvalidPathException("The given path seems wrong");
    }

    if (!errorString.equals("ok")) {
      throw new InvalidPathException(errorString);
    }
  }

  private String checkForError(List<PathSegment> pathSegments, MultivaluedMap<String, String> queryParams)
    throws NumberFormatException {
    HashMap<String, String> pathElems = getPathElements(pathSegments);
    StringBuilder builder = new StringBuilder();
    DataObject dataObject = null;
    Collection collection = null;
    BasicReference reference = null;
    User user = null;

    builder.append("ID ERROR - ");

    if (pathElems.containsKey(Constants.COLLECTIONS)) {
      long id = Long.parseLong(pathElems.get(Constants.COLLECTIONS));
      collection = collectionService.getCollectionByShepardId(id, null);
      String error = checkCollection(collection);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.DATA_OBJECTS)) {
      long id = Long.parseLong(pathElems.get(Constants.DATA_OBJECTS));
      dataObject = dataObjectService.getDataObjectByShepardId(id);
      String error = checkDataObject(dataObject, collection);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.LAB_JOURNAL_ENTRIES)) {
      long id = Long.parseLong(pathElems.get(Constants.LAB_JOURNAL_ENTRIES));
      LabJournalEntry labJournalEntry = labJournalService.getLabJournalEntry(id);
      String error = checkLabJournalEntry(labJournalEntry);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.TIMESERIES_REFERENCES)) {
      long id = Long.parseLong(pathElems.get(Constants.TIMESERIES_REFERENCES));
      var timeseriesReference = timeseriesReferenceService.getReferenceByShepardId(id);
      String error = checkReference(timeseriesReference, dataObject);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.TIMESERIES_CONTAINERS)) {
      long id = Long.parseLong(pathElems.get(Constants.TIMESERIES_CONTAINERS));
      var timeseriesContainer = timeseriesContainerService.getContainer(id);
      String error = checkContainer(timeseriesContainer);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    // This is temporary for the experimental timeseries endpoints
    if (pathElems.containsKey(Constants.EXPERIMENTAL_TIMESERIES_CONTAINERS)) {
      long id = Long.parseLong(pathElems.get(Constants.EXPERIMENTAL_TIMESERIES_CONTAINERS));
      var timeseriesContainer = experimentalTimeseriesContainerService.getContainer(id);
      String error = checkContainer(timeseriesContainer);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.STRUCTURED_DATA_REFERENCES)) {
      long id = Long.parseLong(pathElems.get(Constants.STRUCTURED_DATA_REFERENCES));
      var structuredDataReference = structuredDataReferenceService.getReferenceByShepardId(id);
      String error = checkReference(structuredDataReference, dataObject);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.STRUCTURED_DATA_CONTAINERS)) {
      long id = Long.parseLong(pathElems.get(Constants.STRUCTURED_DATA_CONTAINERS));
      var structuredDataContainer = structuredDataContainerService.getContainer(id);
      String error = checkContainer(structuredDataContainer);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.FILE_REFERENCES)) {
      long id = Long.parseLong(pathElems.get(Constants.FILE_REFERENCES));
      var fileReference = fileReferenceService.getReferenceByShepardId(id);
      String error = checkReference(fileReference, dataObject);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.FILE_CONTAINERS)) {
      long id = Long.parseLong(pathElems.get(Constants.FILE_CONTAINERS));
      var fileContainer = fileContainerService.getContainer(id);
      String error = checkContainer(fileContainer);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.COLLECTION_REFERENCES)) {
      long id = Long.parseLong(pathElems.get(Constants.COLLECTION_REFERENCES));
      var collectionReference = collectionReferenceService.getReferenceByShepardId(id);
      String error = checkReference(collectionReference, dataObject);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.DATAOBJECT_REFERENCES)) {
      long id = Long.parseLong(pathElems.get(Constants.DATAOBJECT_REFERENCES));
      var dataObjectReference = dataObjectReferenceService.getReferenceByShepardId(id);
      String error = checkReference(dataObjectReference, dataObject);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.URI_REFERENCES)) {
      long id = Long.parseLong(pathElems.get(Constants.URI_REFERENCES));
      var uriReferences = uriReferenceService.getReferenceByShepardId(id);
      String error = checkReference(uriReferences, dataObject);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.BASIC_REFERENCES)) {
      long id = Long.parseLong(pathElems.get(Constants.BASIC_REFERENCES));
      reference = basicReferenceService.getReferenceByShepardId(id);
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
      var subscription = subscriptionService.getSubscription(id);
      String error = checkSubscription(subscription, user);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.USERGROUPS)) {
      long id = Long.parseLong(pathElems.get(Constants.USERGROUPS));
      var usergroup = userGroupService.getUserGroup(id);
      String error = checkUserGroup(usergroup);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.SEMANTIC_REPOSITORIES)) {
      long id = Long.parseLong(pathElems.get(Constants.SEMANTIC_REPOSITORIES));
      var semanticRepository = semanticRepositoryService.getRepository(id);
      String error = checkSemanticRepository(semanticRepository);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    if (pathElems.containsKey(Constants.SEMANTIC_ANNOTATIONS)) {
      long id = Long.parseLong(pathElems.get(Constants.SEMANTIC_ANNOTATIONS));
      var annotation = semanticAnnotationService.getAnnotationByNeo4jId(id);
      String error = checkSemanticAnnotation(annotation, collection, dataObject, reference);
      if (error != null) {
        return builder.append(error).toString();
      }
    }

    //Check for LabJournalEntry calls that do not have id segment
    if (pathSegments.size() > 0 && pathSegments.get(0).getPath().equals(Constants.LAB_JOURNAL_ENTRIES)) {
      String dataObjectId = queryParams.getFirst(Constants.DATA_OBJECT_ID);
      // If the labjournalEntry request has objectId parameter [in GET/labJournals and POST /labJournals]
      if (dataObjectId != null && !dataObjectId.isEmpty() && StringUtils.isNumeric(dataObjectId)) {
        DataObject labJournalDataObject = dataObjectService.getDataObjectByNeo4jId(Long.parseLong(dataObjectId));
        String error = checkDataObject(labJournalDataObject);
        if (error != null) {
          return builder.append(error).toString();
        }
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

  private String checkDataObject(DataObject dataObject) {
    if (dataObject == null) {
      return "DataObject does not exist";
    }
    return null;
  }

  private String checkDataObject(DataObject dataObject, Collection collection) {
    if (dataObject == null) {
      return "DataObject does not exist";
    } else if (!dataObject.getCollection().getShepardId().equals(collection.getShepardId())) {
      return "There is no association between collection and dataObject";
    }
    return null;
  }

  private String checkLabJournalEntry(LabJournalEntry labJournalEntry) {
    if (labJournalEntry == null) {
      return "LabJournalEntry does not exist";
    }
    return null;
  }

  private String checkReference(BasicReference reference, DataObject dataObject) {
    if (reference == null) {
      return "Reference does not exist";
    } else if (!reference.getDataObject().getShepardId().equals(dataObject.getShepardId())) {
      return "There is no association between dataObject and reference";
    }
    return null;
  }

  private String checkContainer(BasicContainer container) {
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
    } else if (user.getApiKeys().stream().noneMatch(aKey -> aKey.getUid().equals(apiKey.getUid()))) {
      return "There is no association between apiKey and user";
    }
    return null;
  }

  private String checkSubscription(Subscription subscription, User user) {
    if (subscription == null) {
      return "Subscription does not exist";
    } else if (user.getSubscriptions().stream().noneMatch(s -> s.getId().equals(subscription.getId()))) {
      return "There is no association between subscription and user";
    }
    return null;
  }

  private String checkUserGroup(UserGroup userGroup) {
    if (userGroup == null) {
      return "UserGroup does not exist";
    }
    return null;
  }

  private String checkSemanticRepository(SemanticRepository semanticRepository) {
    if (semanticRepository == null) {
      return "SemanticRepository does not exist";
    }
    return null;
  }

  private String checkSemanticAnnotation(
    SemanticAnnotation annotation,
    Collection collection,
    DataObject dataObject,
    BasicReference reference
  ) {
    if (annotation == null) {
      return "SemanticAnnotation does not exist";
    } else if (reference != null) {
      // It is a reference annotation
      if (reference.getAnnotations().stream().noneMatch(a -> a.getId().equals(annotation.getId()))) {
        return "There is no association between annotation and reference";
      }
      return null;
    } else if (dataObject != null) {
      // It is a dataObject annotation
      if (dataObject.getAnnotations().stream().noneMatch(a -> a.getId().equals(annotation.getId()))) {
        return "There is no association between annotation and dataObject";
      }
      return null;
    } else if (collection != null) {
      // It is a collection annotation
      if (collection.getAnnotations().stream().noneMatch(a -> a.getId().equals(annotation.getId()))) {
        return "There is no association between annotation and collection";
      }
      return null;
    }
    return "No entity was found annotated";
  }

  private HashMap<String, String> getPathElements(List<PathSegment> pathSegments) {
    HashMap<String, String> pathElems = new HashMap<>();
    for (int i = 0; i + 1 < pathSegments.size(); i = i + 2) {
      String value = pathSegments.get(i).getPath();
      String id = pathSegments.get(i + 1).getPath();
      if (id.isBlank() || id.equals("/")) return pathElems;
      pathElems.put(value, id);
    }

    return pathElems;
  }
}
