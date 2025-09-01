package de.dlr.shepard.common.util;

public class Constants {

  private Constants() {
    // hide public constructor
  }

  public static final String API_KEY_HEADER = "X-API-KEY";

  public static final String HEALTHZ = "healthz";
  public static final String VERSIONZ = "versionz";

  public static final String USER = "user";
  public static final String USERS = "users";
  public static final String USERNAME = "username";
  public static final String USERGROUP = "userGroup";
  public static final String USERGROUPS = "userGroups";
  public static final String USERGROUP_ID = "userGroupId";
  public static final String FIRSTNAME = "firstName";
  public static final String LASTNAME = "lastName";
  public static final String EMAIL = "email";

  public static final String SHEPARD_ID = "shepardId";

  public static final String APIKEY = "apikey";
  public static final String APIKEYS = "apikeys";
  public static final String APIKEY_UID = "apikeyUid";

  public static final String SUBSCRIPTION = "subscription";
  public static final String SUBSCRIPTIONS = "subscriptions";
  public static final String SUBSCRIPTION_ID = "subscriptionId";

  public static final String COLLECTION = "collection";
  public static final String COLLECTIONS = "collections";
  public static final String COLLECTION_ID = "collectionId";

  public static final String DATA_OBJECT = "dataObject";
  public static final String DATA_OBJECTS = "dataObjects";
  public static final String DATA_OBJECT_ID = "dataObjectId";

  public static final String BASIC_REFERENCE = "reference";
  public static final String BASIC_REFERENCES = "references";
  public static final String BASIC_REFERENCE_ID = "referenceId";

  public static final String STRUCTURED_DATA_CONTAINER = "structuredDataContainer";
  public static final String STRUCTURED_DATA_CONTAINERS = "structuredDataContainers";
  public static final String STRUCTURED_DATA_CONTAINER_ID = "structuredDataContainerId";
  public static final String STRUCTURED_DATA_REFERENCE = "structuredDataReference";
  public static final String STRUCTURED_DATA_REFERENCES = "structuredDataReferences";
  public static final String STRUCTURED_DATA_REFERENCE_ID = "structuredDataReferenceId";

  public static final String FILE = "file";
  public static final String FILE_CONTAINER = "fileContainer";
  public static final String FILE_CONTAINERS = "fileContainers";
  public static final String FILE_CONTAINER_ID = "fileContainerId";
  public static final String FILE_REFERENCE = "fileReference";
  public static final String FILE_REFERENCES = "fileReferences";
  public static final String FILE_REFERENCE_ID = "fileReferenceId";

  public static final String SEMANTIC_REPOSITORY = "semanticRepository";
  public static final String SEMANTIC_REPOSITORIES = "semanticRepositories";
  public static final String SEMANTIC_REPOSITORY_ID = "semanticRepositoryId";
  public static final String SEMANTIC_ANNOTATION = "semanticAnnotation";
  public static final String SEMANTIC_ANNOTATIONS = "semanticAnnotations";
  public static final String SEMANTIC_ANNOTATION_ID = "semanticAnnotationId";

  public static final String OID = "oid";

  public static final String SEARCH = "search";

  public static final String EXPORT = "export";

  public static final String CONTAINERS = "containers";

  public static final String TIMESERIES = "timeseries";
  public static final String TIMESERIES_ID = "timeseriesId";
  public static final String TIMESERIES_CONTAINER = "timeseriesContainer";
  public static final String TIMESERIES_CONTAINERS = "timeseriesContainers";
  public static final String TIMESERIES_CONTAINER_ID = "timeseriesContainerId";
  public static final String TIMESERIES_REFERENCE = "timeseriesReference";
  public static final String TIMESERIES_REFERENCES = "timeseriesReferences";
  public static final String TIMESERIES_REFERENCE_ID = "timeseriesReferenceId";
  public static final String PAYLOAD = "payload";
  public static final String AVAILABLE = "available";
  public static final String IMPORT = "import";
  public static final String METRIC = "metric";
  public static final String METRICS = "metrics";

  public static final String DATAOBJECT_REFERENCE = "dataObjectReference";
  public static final String DATAOBJECT_REFERENCES = "dataObjectReferences";
  public static final String DATAOBJECT_REFERENCE_ID = "dataObjectReferenceId";

  public static final String COLLECTION_REFERENCE = "collectionReference";
  public static final String COLLECTION_REFERENCES = "collectionReferences";
  public static final String COLLECTION_REFERENCE_ID = "collectionReferenceId";

  public static final String URI_REFERENCE = "uriReference";
  public static final String URI_REFERENCES = "uriReferences";
  public static final String URI_REFERENCE_ID = "uriReferenceId";

  public static final String PERMISSIONS = "permissions";
  public static final String PERMISSION_ID = "permissionId";

  public static final String LAB_JOURNAL_ENTRY = "labJournalEntry";
  public static final String LAB_JOURNAL_ENTRIES = "labJournalEntries";
  public static final String LAB_JOURNAL_ENTRY_ID = "labJournalEntryId";

  public static final String ROLES = "roles";
  public static final String VERSION = "version";
  public static final String VERSIONS = "versions";
  public static final String INITIAL_VERSION = "initial version";
  public static final String HEAD = "HEAD";
  public static final String HEAD_VERSION = "HEAD version";
  public static final String VERSION_UID = "versionUid";

  public static final String OWNED_BY = "owned_by";
  public static final String READABLE_BY = "readable_by";
  public static final String WRITEABLE_BY = "writeable_by";
  public static final String MANAGEABLE_BY = "manageable_by";
  public static final String HAS_PERMISSIONS = "has_permissions";
  public static final String READABLE_BY_GROUP = "readable_by_group";
  public static final String WRITEABLE_BY_GROUP = "writeable_by_group";
  public static final String HAS_VERSION = "has_version";

  // Query Params
  public static final String QP_NAME = "name";
  public static final String QP_PAGE = "page";
  public static final String QP_SIZE = "size";
  public static final String QP_PARENT_ID = "parentId";
  public static final String QP_PREDECESSOR_ID = "predecessorId";
  public static final String QP_SUCCESSOR_ID = "successorId";
  public static final String QP_ORDER_BY_ATTRIBUTE = "orderBy";
  public static final String QP_ORDER_DESC = "orderDesc";

  // Relationships
  public static final String HAS_DATAOBJECT = "has_dataobject";
  public static final String HAS_REFERENCE = "has_reference";
  public static final String HAS_CHILD = "has_child";
  public static final String HAS_SUCCESSOR = "has_successor";
  public static final String HAS_PREDECESSOR = "has_predecessor";
  public static final String CREATED_BY = "created_by";
  public static final String UPDATED_BY = "updated_by";
  public static final String BELONGS_TO = "belongs_to";
  public static final String SUBSCRIBED_BY = "subscribed_by";
  public static final String POINTS_TO = "points_to";
  public static final String IS_IN_CONTAINER = "is_in_container";
  public static final String IS_IN_GROUP = "is_in_group";
  public static final String VALUE_REPOSITORY = "value_in_repository";
  public static final String PROPERTY_REPOSITORY = "property_in_repository";
  public static final String HAS_ANNOTATION = "has_annotation";
  public static final String FILE_IN_CONTAINER = "file_in_container";
  public static final String TIMESERIES_IN_CONTAINER = "timeseries_in_container";
  public static final String STRUCTUREDDATA_IN_CONTAINER = "structureddata_in_container";
  public static final String HAS_PAYLOAD = "has_payload";
  public static final String HAS_LABJOURNAL_ENTRY = "has_labjournalentry";
  public static final String HAS_DEFAULT_FILE_CONTAINER = "has_default_file_container";

  // Influx
  public static final String MEASUREMENT = "measurement";
  public static final String LOCATION = "location";
  public static final String DEVICE = "device";
  public static final String SYMBOLICNAME = "symbolic_name";
  public static final String FIELD = "field";
  public static final String START = "start";
  public static final String END = "end";
  public static final String FUNCTION = "function";
  public static final String GROUP_BY = "group_by";
  public static final String FILLOPTION = "fill_option";

  // Search
  public static final String OP_PROPERTY = "property";
  public static final String OP_VALUE = "value";
  public static final String OP_OPERATOR = "operator";
  public static final String COLLECTION_IN_QUERY = "col";
  public static final String DATAOBJECT_IN_QUERY = "do";
  public static final String REFERENCE_IN_QUERY = "br";
  public static final String ANNOTATABLE_TS_IN_QUERY = "ats";
  public static final String FILECONTAINER_IN_QUERY = "fc";
  public static final String TIMESERIESCONTAINER_IN_QUERY = "tsc";
  public static final String STRUCTUREDDATACONTAINER_IN_QUERY = "sdc";
  public static final String BASICCONTAINER_IN_QUERY = "bc";
  public static final String SPATIALDATACONTAINER_IN_QUERY = "spdc";
  public static final String USER_IN_QUERY = "user";
  public static final String USERGROUP_IN_QUERY = "userGroup";
  public static final String PAYLOAD_IN_QUERY = "pl";
  public static final String ANNOTATION_IN_QUERY = "sem";
  public static final String JSON_AND = "AND";
  public static final String JSON_OR = "OR";
  public static final String JSON_NOT = "NOT";
  public static final String JSON_XOR = "XOR";
  public static final String JSON_EQ = "eq";
  public static final String JSON_CONTAINS = "contains";
  public static final String JSON_REGMATCH = "regmatch";
  public static final String JSON_GT = "gt";
  public static final String JSON_LT = "lt";
  public static final String JSON_GE = "ge";
  public static final String JSON_LE = "le";
  public static final String JSON_IN = "in";
  public static final String JSON_NE = "ne";

  // spatial data
  public static final String SPATIAL_DATA_CONTAINER = "spatialDataContainer";
  public static final String SPATIAL_DATA_CONTAINERS = "spatialDataContainers";
  public static final String SPATIAL_DATA_CONTAINER_ID = "spatialDataContainerId";
  public static final String SPATIAL_DATA_REFERENCES = "spatialDataReferences";
  public static final String SPATIAL_DATA_REFERENCE = "spatialDataReference";
  public static final String SPATIAL_DATA_REFERENCE_ID = "spatialDataReferenceId";
}
